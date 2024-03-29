package model

import scala.concurrent.Future
import lib.{AWS, AmazonConnection, FutureO}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._
import scala.util.Try
import play.api.Logger
import play.api.libs.json._
import controllers.routes
import com.amazonaws.services.cloudwatch.model.{Datapoint, Dimension, GetMetricStatisticsRequest}
import com.amazonaws.services.cloudwatch.model.Statistic._
import com.amazonaws.services.ec2
import org.joda.time.DateTime
import play.api.libs.ws.WSClient

case class ASG(name: Option[String], stage: Option[String], app: Option[String], stack: Option[String],
  elb: Option[ELB], members: Seq[ASGMember], recentActivity: Seq[ScalingAction],
  cpu: Seq[Datapoint], suspendedActivities: Seq[String], approxMonthlyCost: Option[BigDecimal],
  moreDetailsLink: Option[String], hint: Option[String])

object ASG {

  import AWS.Writes._

  implicit val writes = Json.writes[ASG]

  type ASGHint = PartialFunction[Map[String, String], String]

  def tagMatcher(tagName: String)(pf: PartialFunction[String, String]): ASGHint = {
    case tags if tags.contains(tagName) && pf.isDefinedAt(tags(tagName)) => pf(tags(tagName))
  }

  val elasticsearchMaster = tagMatcher("es-config:node.master") {
    case "true" => "es-master"
  }

  val elasticsearchHotWarm = tagMatcher("es-config:node.attr.data") {
    case s => "es-" + s
  }

  val hintProvider: ASGHint = elasticsearchMaster orElse elasticsearchHotWarm

}

class ASGSource(cost: AWSCost) {
  val log = Logger(classOf[ASGSource])

  def fromApp(instances: List[com.amazonaws.services.ec2.model.Instance])(implicit conn: AmazonConnection, ws: WSClient): Future[Seq[ASG]] = {

    val instancesByAutoScalingGroupName: Map[Option[String], Seq[ec2.model.Instance]] =
      instances.groupBy(_.getTags.asScala.find(_.getKey == "aws:autoscaling:groupName").map(_.getValue))

    Future.traverse(instancesByAutoScalingGroupName.toSeq) {
      case (autoScalingGroupNameOpt, instancesOfGroupName) => fromInstancesWithAutoscalingGroupName(autoScalingGroupNameOpt, instancesOfGroupName)
    }
  }

  private def fromInstancesWithAutoscalingGroupName(autoScalingGroupNameOpt: Option[String], instances: Seq[ec2.model.Instance])
    (implicit conn: AmazonConnection, ws: WSClient): Future[ASG] = {
    val tags = instances.flatMap(i => i.getTags.asScala.toList.map(t => t.getKey -> t.getValue)).toMap

    val asgFtO = for {
      asgName <- FutureO.toFut(autoScalingGroupNameOpt)
      asg <- FutureO(EstateInstances.fetchAsgByName(asgName))
    } yield asg

    val awsAsgInstances = for {
      asg <- asgFtO
    } yield asg.getInstances.asScala.toList

    val elbOptFt: FutureO[ELB] = for {
      asg <- asgFtO
      elbName <- FutureO.toFut(asg.getLoadBalancerNames.asScala.headOption)
      elb <- FutureO.toOpt(ELB.forName(elbName))
    } yield elb

    val recentActivity = for {
      asg <- asgFtO
      actions <- FutureO.toOpt(ScalingAction.forGroup(asg.getAutoScalingGroupName))
    } yield actions filter (_.isRecent)

    val cpuFtO = for {
      asg <- asgFtO
      stats <- FutureO.toOpt(AWS.futureOf(conn.cloudWatch.getMetricStatisticsAsync, new GetMetricStatisticsRequest()
        .withDimensions(new Dimension().withName("AutoScalingGroupName").withValue(asg.getAutoScalingGroupName))
        .withMetricName("CPUUtilization").withNamespace("AWS/EC2").withPeriod(60)
        .withStatistics(Maximum, Average)
        .withStartTime(DateTime.now().minusHours(3).toDate).withEndTime(DateTime.now().toDate)
      ))
    } yield stats.getDatapoints.asScala.toList.sortBy(_.getTimestamp)

    val suspendedProcesses = for {
      asg <- asgFtO
      processes = asg.getSuspendedProcesses.asScala.toList.map(_.getProcessName).sorted
    } yield processes

    for {
      elbOpt <- elbOptFt.futureOption
      membersOfElb = elbOpt.map(_.members).getOrElse(Nil)
      membersOfASGOpt <- awsAsgInstances.futureOption
      membersOfASG = membersOfASGOpt.getOrElse(Nil)
      clusterMembers = Future.sequence(instances.map(i => Instance.from(i, cost).map(i => ASGMember.from(i, membersOfASG.find(_.getInstanceId == i.id), membersOfElb.find(_.id == i.id)))))
      members <- clusterMembers
      activities <- recentActivity.futureOption
      cpu <- cpuFtO.futureOption
      susPro <- suspendedProcesses.futureOption
    } yield {
      val moreDetailsLink = ManagementTag(tags.get("Management")).flatMap { t =>
        if (t.format == Some("elasticsearch")) {
          autoScalingGroupNameOpt.map(name => (routes.Application.es(name).url))
        } else None
      }
      ASG(
        name = autoScalingGroupNameOpt,
        stage = tags.get("Stage"),
        app = tags.get("App") orElse tags.get("Role"),
        stack = tags.get("Stack"),
        elb = elbOpt,
        members = members.sortBy(_.instance.availabilityZone),
        recentActivity = activities.getOrElse(Nil),
        cpu = cpu.getOrElse(Nil),
        suspendedActivities = susPro.getOrElse(Nil),
        approxMonthlyCost = Try(members.flatMap(_.instance.approxMonthlyCost).sum).toOption,
        moreDetailsLink = moreDetailsLink,
        hint = ASG.hintProvider.lift(tags)
      )
    }
  }

}

object FutureOption {
  def apply[T](of: Option[Future[T]]): Future[Option[T]] =
    of.map(f => f.map(Some(_))) getOrElse (Future.successful(None))
}
