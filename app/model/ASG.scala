package model

import scala.concurrent.Future
import lib.{AWS, AmazonConnection}
import scala.concurrent.ExecutionContext.Implicits.global

import com.amazonaws.services.autoscaling.model.{Instance => AwsAsgInstance, _}

import collection.JavaConversions._
import scala.util.{Random, Try}
import play.api.Logger
import play.api.libs.ws.WS
import play.api.libs.json._
import controllers.routes
import com.amazonaws.services.cloudwatch.model.{Datapoint, Dimension, GetMetricStatisticsRequest}
import org.joda.time.DateTime
import scala.Some
import play.api.libs.json.JsObject

case class WebAppASG(asg: AutoScalingGroup, elb: Option[ELB], recentActivity: Seq[ScalingAction],
                     members: Seq[ClusterMember], averageCPU: Seq[Datapoint])
  extends ASG

case class ElasticSearchASG(asg: AutoScalingGroup, elb: Option[ELB], recentActivity: Seq[ScalingAction],
                            members: Seq[ClusterMember], averageCPU: Seq[Datapoint], esStats: JsValue)
  extends ASG {
  override def moreDetailsLink = Some(routes.Application.es(name).url)

  lazy val elasticSearchStats = {
    Index("All indexes", esStats \ "_all") :: (esStats \ "indices" match {
      case JsObject(indexes) => indexes.toList map {
        case (k, v) => Index(k, v)
      }
    }).sortBy(_.name)
  }

}
case class StatsGroup(name: String, queryTime: Long, queryCount: Long, humanTime: String) {
  lazy val averageRequestTime = if (queryCount == 0) 0 else queryTime / queryCount
}
object StatsGroup {
  def apply(name: String, v: JsValue): StatsGroup =
    StatsGroup(name, (v \ "query_time_in_millis").as[Long], (v \ "query_total").as[Long], (v \ "query_time").as[String])
}
case class Index(name: String, statsGroups: Seq[StatsGroup])
object Index {
  def apply(name: String, v: JsValue): Index = {
    val allSearch = v \ "total" \ "search"
    val stats = StatsGroup("(overall)", allSearch) ::
      (allSearch \ "groups" match {
        case JsObject(groups) => groups.toList map {
          case (k, v) => StatsGroup(k, v)
        }
        case _ => Nil
      }).sortBy(- _.queryTime)
    Index(name, stats)
  }
}

trait ASG {
  val asg: AutoScalingGroup
  val elb: Option[ELB]
  val recentActivity: Seq[ScalingAction]
  val members: Seq[ClusterMember]
  val averageCPU: Seq[Datapoint]

  lazy val name = asg.getAutoScalingGroupName
  lazy val tags = asg.getTags.map(t => t.getKey -> t.getValue).toMap

  lazy val stage = tags.get("Stage").getOrElse("?")
  lazy val appName = (tags get "Role") orElse (tags get "App") getOrElse "?"

  lazy val hasElb = elb.isDefined

  lazy val suspendedActivities = asg.getSuspendedProcesses.toList.map(_.getProcessName).sorted

  lazy val approxMonthlyCost =
    Try(members.map(_.instance.approxMonthlyCost).sum).getOrElse(BigDecimal(0))

  def moreDetailsLink: Option[String] = None
}

case class ClusterMember(asgInfo: AwsAsgInstance, elbInfo: Option[ELB#Member], instance: Instance) {
  def id = asgInfo.getInstanceId
  def healthStatus = asgInfo.getHealthStatus
  def lifecycleState = asgInfo.getLifecycleState

  def state = elbInfo.map(_.state)
  def description = elbInfo.flatMap(_.description)
  def reasonCode = elbInfo.flatMap(_.reasonCode)

  def goodorbad = (healthStatus, lifecycleState, state) match {
    case (_, "Pending", _) | (_, "Terminating", _) => "pending"
    case ("Healthy", "InService", Some("InService")) => "success"
    case ("Healthy", "InService", None) => "success"
    case _ => "danger"
  }
}

object ASG {
  val log = Logger[ASG](classOf[ASG])

  def apply(asg: AutoScalingGroup)(implicit conn: AmazonConnection): Future[ASG] =  {
    log.info(s"Retrieving details for ${asg.getAutoScalingGroupName}")
    val instanceStates = Future.sequence((asg.getLoadBalancerNames.headOption map (ELB(_))).toSeq)

    val recentActivity = for {
      actions <- ScalingAction.forGroup(asg.getAutoScalingGroupName)
    } yield actions filter (_.isRecent)

    val clusterMembers = for {
      elb <- instanceStates
      members <- Future.sequence(asg.getInstances map { m =>
        val membersOfElb = elb.headOption.map(_.members).getOrElse(Nil)
        for {
          i <- Instance.get(m.getInstanceId)
        } yield new ClusterMember(m, membersOfElb.find(_.id == m.getInstanceId), i)
      })
    } yield members

    val elasticsSeachStats = if (asg.getTags.exists(t => t.getKey == "Role" && t.getValue.contains("elasticsearch")))
      for {
        members <- clusterMembers
        stats  <- FutureOption(
          (Random.shuffle(members).headOption map (m => WS.url(s"http://${m.instance.publicDns}:9200/_all/_stats?groups=_all").get))
        )
      } yield stats map (_.json)
    else Future.successful(None)

    val stats = AWS.futureOf(conn.cloudWatch.getMetricStatisticsAsync, new GetMetricStatisticsRequest()
      .withDimensions(new Dimension().withName("AutoScalingGroupName").withValue(asg.getAutoScalingGroupName))
      .withMetricName("CPUUtilization").withNamespace("AWS/EC2").withPeriod(60).withStatistics("Average")
      .withStartTime(DateTime.now().minusHours(3).toDate).withEndTime(DateTime.now().toDate)
    )

    for {
      states <- instanceStates
      activities <- recentActivity
      members <- clusterMembers
      asgStats <- stats
      esStats <- elasticsSeachStats recover { case _ => None }
    } yield {
      val averageCPU = asgStats.getDatapoints.sortBy(_.getTimestamp)
      esStats map (
        ElasticSearchASG(asg, states.headOption, activities, members, averageCPU, _)
      ) getOrElse
        WebAppASG(asg, states.headOption, activities, members, averageCPU)
    }
  }

  implicit val datapointWrites = new Writes[Datapoint]{
    override def writes(d: Datapoint) = Json.obj(
      "x" -> d.getTimestamp.getTime,
      "y" -> d.getAverage.toInt
    )
  }

  implicit val memberWrites = new Writes[ClusterMember] {
    def writes(m: ClusterMember) = Json.obj(
      "id" -> m.id,
      "goodorbad" -> m.goodorbad,
      "lifecycleState" -> m.lifecycleState,
      "state" -> m.state,
      "description" -> m.description,
      "uptime" -> m.instance.uptime,
      "version" -> JsString(m.instance.version.getOrElse("?")),
      "url" -> routes.Application.instance(m.id).url
    )
  }

  implicit val scalingActionWrites = new Writes[ScalingAction] {
    override def writes(a: ScalingAction) = Json.obj(
      "age" -> a.age,
      "cause" -> a.cause
    )
  }

  implicit val elbWrites = new Writes[ELB] {
    override def writes(elb: ELB) = Json.obj(
      "name" -> elb.name,
      "latency" -> elb.latency.map(d => new Datapoint().withTimestamp(d.getTimestamp).withAverage(d.getAverage * 1000)),
      "active" -> elb.active
    )
  }

  implicit val writes = new Writes[ASG] {
    def writes(asg: ASG) = Json.obj(
      "name" -> asg.name,
      "appName" -> asg.appName,
      "members" -> asg.members,
      "recentActivity" -> asg.recentActivity,
      "averageCPU" -> asg.averageCPU,
      "elb" -> asg.elb,
      "approxMonthlyCost" -> asg.approxMonthlyCost,
      "moreDetailsLink" -> asg.moreDetailsLink,
      "suspendedActivities" -> asg.suspendedActivities
    )
  }
}

object FutureOption {
  def apply[T](of: Option[Future[T]]): Future[Option[T]] =
    of.map(f => f.map(Some(_))) getOrElse (Future.successful(None))
}

