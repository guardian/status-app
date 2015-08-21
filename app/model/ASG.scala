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

case class ASG(
  arn: String,
  lastUpdated: DateTime,
  name: String,
  stage: Option[String],
  app: Option[String],
  stack: Option[String],
  elb: Option[ELB],
  members: Seq[ASGMember],
  recentActivity: Seq[ScalingAction],
  cpu: Seq[Datapoint],
  suspendedActivities: Seq[String],
  approxMonthlyCost: Option[BigDecimal],
  moreDetailsLink: Option[String])

object ASG {
  val log = Logger[ASG](classOf[ASG])

  def from(asg: AutoScalingGroup)(implicit conn: AmazonConnection): Future[ASG] =  {
    log.debug(s"Retrieving details for ${asg.getAutoScalingGroupName}")

    val futElb = FutureOption(asg.getLoadBalancerNames.headOption map ELB.forName)
    val futActivities = recentActivity(asg)
    val futAsgStats = cpuStats(asg)

    val name = asg.getAutoScalingGroupName
    val tags = asg.getTags.map(t => t.getKey -> t.getValue).toMap

    for {
      elb <- futElb
      activities <- futActivities
      asgStats <- futAsgStats
    } yield {
      val members = clusterMembers(asg, elb.map(_.members).getOrElse(Nil))
      ASG(
        arn = asg.getAutoScalingGroupARN,
        lastUpdated = DateTime.now,
        name = name,
        stage = tags.get("Stage"),
        app = tags.get("App") orElse tags.get("Role"),
        stack = tags.get("Stack"),
        elb = elb,
        members = members.sortBy(_.instance.availabilityZone),
        recentActivity = activities,
        cpu = asgStats.getDatapoints.sortBy(_.getTimestamp),
        suspendedActivities = asg.getSuspendedProcesses.toList.map(_.getProcessName).sorted,
        approxMonthlyCost = Try(members.flatMap(_.instance.approxMonthlyCost).sum).toOption,
        moreDetailsLink = moreDetailsLink(name, tags)
      )
    }
  }

  import AWS.Writes._

  implicit val writes = Json.writes[ASG]

  private def clusterMembers(asg: AutoScalingGroup, membersOfElb: List[ELBMember]) = for {
    asgInstance <- asg.getInstances
    instance <- Instances.getById(asgInstance.getInstanceId)
  } yield ASGMember.from(asgInstance, membersOfElb.find(_.id == asgInstance.getInstanceId), instance)

  private def cpuStats(asg: AutoScalingGroup)(implicit conn: AmazonConnection) = {
    AWS.futureOf(conn.cloudWatch.getMetricStatisticsAsync, new GetMetricStatisticsRequest()
      .withDimensions(new Dimension().withName("AutoScalingGroupName").withValue(asg.getAutoScalingGroupName))
      .withMetricName("CPUUtilization").withNamespace("AWS/EC2").withPeriod(60)
      .withStatistics(Maximum, Average)
      .withStartTime(DateTime.now().minusHours(3).toDate).withEndTime(DateTime.now().toDate)
    )
  }

  private def recentActivity(asg: AutoScalingGroup)(implicit conn: AmazonConnection) =
    ScalingAction.forGroup(asg.getAutoScalingGroupName) map { _.filter(_.isRecent) }

  private def moreDetailsLink(name: String, tags: Map[String, String]) = {
    ManagementTag(tags.get("Management")) flatMap { t =>
      if (t.format.contains("elasticsearch"))
        Some(routes.Application.es(name).url)
      else
        None
    }
  }
}

object FutureOption {
  def apply[T](of: Option[Future[T]]): Future[Option[T]] =
    of.map(f => f.map(Some(_))) getOrElse (Future.successful(None))
}

