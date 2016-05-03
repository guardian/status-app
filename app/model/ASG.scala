package model

import scala.concurrent.Future
import lib.{FutureO, AWS, AmazonConnection}
import scala.concurrent.ExecutionContext.Implicits.global

import com.amazonaws.services.autoscaling.model.{Instance => AwsAsgInstance, _}

import collection.JavaConversions._
import scala.util.{Random, Try}
import play.api.Logger
import play.api.libs.json._
import controllers.routes
import com.amazonaws.services.cloudwatch.model.{Datapoint, Dimension, GetMetricStatisticsRequest}
import com.amazonaws.services.cloudwatch.model.Statistic._
import org.joda.time.DateTime

case class ASG(name: String, stage: Option[String], app: Option[String], stack: Option[String],
                 elb: Option[ELB], members: Seq[ASGMember], recentActivity: Seq[ScalingAction],
                 cpu: Seq[Datapoint],suspendedActivities: Seq[String], approxMonthlyCost: Option[BigDecimal],
                 moreDetailsLink: Option[String])

object ASG {
  val log = Logger[ASG](classOf[ASG])

  def fromApp(tags: Map[String, String], instances: List[com.amazonaws.services.ec2.model.Instance])(implicit conn: AmazonConnection): Future[ASG] = {
    val autoScalingGroupNameOpt = tags.get("aws:autoscaling:groupName")

    val asgFtO = for {
      asgName <- FutureO.toFut(autoScalingGroupNameOpt)
      asg <- FutureO(Estate.fetchAsgByName(asgName))
    } yield asg

    val elbOptFt: FutureO[ELB] = for {
      asg <- asgFtO
      elbName <- FutureO.toFut(asg.getLoadBalancerNames.headOption)
      elb <- FutureO.toOpt(ELB.forName(elbName))
    } yield elb

    val recentActivity = for {
      asg <- asgFtO
      actions <- FutureO.toOpt(ScalingAction.forGroup(asg.getAutoScalingGroupName))
    } yield actions filter (_.isRecent)

    val cpuFtO = for {
      asg <- asgFtO
      stats <-  FutureO.toOpt(AWS.futureOf(conn.cloudWatch.getMetricStatisticsAsync, new GetMetricStatisticsRequest()
        .withDimensions(new Dimension().withName("AutoScalingGroupName").withValue(asg.getAutoScalingGroupName))
        .withMetricName("CPUUtilization").withNamespace("AWS/EC2").withPeriod(60)
        .withStatistics(Maximum, Average)
        .withStartTime(DateTime.now().minusHours(3).toDate).withEndTime(DateTime.now().toDate)
      ))
    } yield stats.getDatapoints.sortBy(_.getTimestamp)

    val suspendedProcesses = for {
      asg <- asgFtO
      processes = asg.getSuspendedProcesses.toList.map(_.getProcessName).sorted
    } yield processes

    val clusterMembers = Future.sequence(instances.map(i => Instance.from(i).map(i => ASGMember.from(i))))
    for {
      elbOpt <- elbOptFt.futureOption
      members <- clusterMembers
      activities <- recentActivity.futureOption
      cpu <- cpuFtO.futureOption
      susPro <- suspendedProcesses.futureOption
    } yield {
      ASG(
        autoScalingGroupNameOpt.getOrElse("noASG"), tags.get("Stage"), tags.get("App") orElse tags.get("Role"), tags.get("Stack"),
        elbOpt, members.sortBy(_.instance.availabilityZone), activities.getOrElse(Nil),
        cpu.getOrElse(Nil), susPro.getOrElse(Nil),
        Try(members.flatMap(_.instance.approxMonthlyCost).sum).toOption, None
      )
    }
  }

  def from(asg: AutoScalingGroup)(implicit conn: AmazonConnection): Future[ASG] =  {
    import play.api.Play.current
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
          //this is where instances are populated by ASG.
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

