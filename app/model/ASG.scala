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
import com.amazonaws.services.cloudwatch.model.Statistic._
import org.joda.time.DateTime
import play.api.libs.json.JsObject

case class WebAppASG(asg: AutoScalingGroup, elb: Option[ELB], recentActivity: Seq[ScalingAction],
                     members: Seq[ClusterMember], cpu: Seq[Datapoint])
  extends ASG

trait ASG {
  val asg: AutoScalingGroup
  val elb: Option[ELB]
  val recentActivity: Seq[ScalingAction]
  val members: Seq[ClusterMember]
  val cpu: Seq[Datapoint]

  lazy val name = asg.getAutoScalingGroupName
  lazy val tags = asg.getTags.map(t => t.getKey -> t.getValue).toMap

  lazy val stage = tags.get("Stage").getOrElse("?")
  lazy val appName = (tags get "Role") orElse (tags get "App") getOrElse "?"

  lazy val hasElb = elb.isDefined

  lazy val suspendedActivities = asg.getSuspendedProcesses.toList.map(_.getProcessName).sorted

  lazy val approxMonthlyCost =
    Try(members.map(_.instance.approxMonthlyCost).sum).getOrElse(BigDecimal(0))

  def moreDetailsLink: Option[String] = ManagementTag(tags.get("Management")).flatMap { t =>
    if (t.format == Some("elasticsearch")) Some(routes.Application.es(name).url) else None
  }
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

    val stats = AWS.futureOf(conn.cloudWatch.getMetricStatisticsAsync, new GetMetricStatisticsRequest()
      .withDimensions(new Dimension().withName("AutoScalingGroupName").withValue(asg.getAutoScalingGroupName))
      .withMetricName("CPUUtilization").withNamespace("AWS/EC2").withPeriod(60)
      .withStatistics(Maximum, Average)
      .withStartTime(DateTime.now().minusHours(3).toDate).withEndTime(DateTime.now().toDate)
    )

    for {
      states <- instanceStates
      activities <- recentActivity
      members <- clusterMembers
      asgStats <- stats
    } yield {
      val maxCPU = asgStats.getDatapoints.sortBy(_.getTimestamp)
      WebAppASG(asg, states.headOption, activities, members, maxCPU)
    }
  }

  implicit val datapointWrites = new Writes[Datapoint]{
    override def writes(d: Datapoint) = Json.obj(
      "time" -> d.getTimestamp.getTime,
      "average" -> Option(d.getAverage).map(_.toInt),
      "maximum" -> Option(d.getMaximum).map(_.toInt)
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
      "cpu" -> asg.cpu,
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

