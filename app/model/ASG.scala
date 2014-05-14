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

case class ASG(name: String, stage: Option[String], app: Option[String], stack: Option[String],
                 elb: Option[ELB], members: Seq[ASGMember], recentActivity: Seq[ScalingAction],
                 cpu: Seq[Datapoint],suspendedActivities: Seq[String], approxMonthlyCost: Option[BigDecimal])

object ASG {
  val log = Logger[ASG](classOf[ASG])

  def from(asg: AutoScalingGroup)(implicit conn: AmazonConnection): Future[ASG] =  {
    log.debug(s"Retrieving details for ${asg.getAutoScalingGroupName}")
    val elb = FutureOption((asg.getLoadBalancerNames.headOption map (ELB.forName(_))))

    val recentActivity = for {
      actions <- ScalingAction.forGroup(asg.getAutoScalingGroupName)
    } yield actions filter (_.isRecent)

    val clusterMembers = for {
      lb <- elb
      members <- Future.sequence(asg.getInstances map { m =>
        val membersOfElb = lb.map(_.members).getOrElse(Nil)
        for {
          i <- Instance.get(m.getInstanceId)
        } yield ASGMember.from(m, membersOfElb.find(_.id == m.getInstanceId), i)
      })
    } yield members

    val stats = AWS.futureOf(conn.cloudWatch.getMetricStatisticsAsync, new GetMetricStatisticsRequest()
      .withDimensions(new Dimension().withName("AutoScalingGroupName").withValue(asg.getAutoScalingGroupName))
      .withMetricName("CPUUtilization").withNamespace("AWS/EC2").withPeriod(60)
      .withStatistics(Maximum, Average)
      .withStartTime(DateTime.now().minusHours(3).toDate).withEndTime(DateTime.now().toDate)
    )

    for {
      lb <- elb
      activities <- recentActivity
      members <- clusterMembers
      asgStats <- stats
    } yield {
      val cpu = asgStats.getDatapoints.sortBy(_.getTimestamp)
      val tags = asg.getTags.map(t => t.getKey -> t.getValue).toMap
      ASG(
        asg.getAutoScalingGroupName, tags.get("Stage"), tags.get("App") orElse tags.get("Role"), tags.get("Stack"),
        lb, members, activities, cpu, asg.getSuspendedProcesses.toList.map(_.getProcessName).sorted,
        Try(members.flatMap(_.instance.approxMonthlyCost).sum).toOption
      )
    }
  }

  import AWS.Writes._

  implicit val writes = Json.writes[ASG]
}

object FutureOption {
  def apply[T](of: Option[Future[T]]): Future[Option[T]] =
    of.map(f => f.map(Some(_))) getOrElse (Future.successful(None))
}

