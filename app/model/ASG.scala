package model

import com.amazonaws.services.ec2.model.{Instance => AwsEc2Instance, DescribeInstancesRequest}
import io.getclump.Clump

import scala.concurrent.Future
import lib.{AWS, AmazonConnection}
import scala.concurrent.ExecutionContext.Implicits.global

import com.amazonaws.services.autoscaling.model.{Instance => AwsAsgInstance, _}

import collection.convert.wrapAll._
import scala.util.{Random, Try}
import play.api.Logger
import play.api.libs.ws.WS
import play.api.libs.json._
import controllers.routes
import com.amazonaws.services.cloudwatch.model.{Datapoint, Dimension, GetMetricStatisticsRequest}
import com.amazonaws.services.cloudwatch.model.Statistic._
import org.joda.time.DateTime
import play.api.libs.json.JsObject

case class ASG(name: String, stage: Option[String], app: Option[String], stack: Option[String],
                 elb: Option[ELB], members: Seq[ASGMember], recentActivity: Seq[ScalingAction],
                 cpu: Seq[Datapoint],suspendedActivities: Seq[String], approxMonthlyCost: Option[BigDecimal],
                 moreDetailsLink: Option[String])

object ASG {
  import io.getclump.FutureBridge._

  val log = Logger[ASG](classOf[ASG])

  def from(asg: AutoScalingGroup)(implicit conn: AmazonConnection): Future[ASG] =  {
    import play.api.Play.current
    log.debug(s"Retrieving details for ${asg.getAutoScalingGroupName}")
    val elb = FutureOption((asg.getLoadBalancerNames.headOption map (ELB.forName(_))))

    val recentActivity = for {
      actions <- ScalingAction.forGroup(asg.getAutoScalingGroupName)
    } yield actions filter (_.isRecent)

    def instanceDetails(ids: List[String]): Future[Seq[AwsEc2Instance]] =
      for (result<- AWS.futureOf(conn.ec2.describeInstancesAsync, new DescribeInstancesRequest().withInstanceIds(ids :_*)))
        yield result.getReservations flatMap (_.getInstances)

    val instanceSource = Clump.source(instanceDetails)(_.getInstanceId)

    val clump = Clump.traverse(asg.getInstances.toList) { m =>
      for {
        elb <- Clump.future(elb)
        i <- instanceSource.get(m.getInstanceId)
        instance <- Clump.future(Instance.from(i))
      } yield ASGMember.from(m, elb.members.find(_.id == m.getInstanceId), instance)
    }

    val clusterMembers = clump.list

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
      val name = asg.getAutoScalingGroupName
      val moreDetailsLink = ManagementTag(tags.get("Management")).flatMap { t =>
        if (t.format == Some("elasticsearch")) Some(routes.Application.es(name).url) else None
      }
      ASG(
        name, tags.get("Stage"), tags.get("App") orElse tags.get("Role"), tags.get("Stack"),
        lb, members.sortBy(_.instance.availabilityZone), activities, cpu, asg.getSuspendedProcesses.toList.map(_.getProcessName).sorted,
        Try(members.flatMap(_.instance.approxMonthlyCost).sum).toOption, moreDetailsLink
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

