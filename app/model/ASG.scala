package model

import scala.concurrent.Future
import lib.AmazonConnection
import scala.concurrent.ExecutionContext.Implicits.global

import com.amazonaws.services.autoscaling.model.{Instance => AwsAsgInstance, _}

import collection.JavaConversions._
import scala.util.Try
import play.api.Logger
import play.api.libs.ws.WS
import play.api.libs.json.JsValue
import controllers.routes

case class WebAppASG(asg: AutoScalingGroup, elb: Option[ELB], recentActivity: Seq[ScalingAction], members: Seq[ClusterMember])
  extends ASG

case class ElasticSearchASG(asg: AutoScalingGroup, elb: Option[ELB], recentActivity: Seq[ScalingAction],
                            members: Seq[ClusterMember], esStats: JsValue)
  extends ASG {
  override def moreDetailsLink = Some(routes.Application.es.url)
}

trait ASG {
  val asg: AutoScalingGroup
  val elb: Option[ELB]
  val recentActivity: Seq[ScalingAction]
  val members: Seq[ClusterMember]

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
    log.info(s"Retrieveing details for ${asg.getAutoScalingGroupName}")
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
          (members.headOption map (m => WS.url(s"http://${m.instance.publicDns}:9200/_all/_stats?groups=_all").get))
        )
      } yield stats map (_.json)
    else Future.successful(None)

    for {
      states <- instanceStates
      activities <- recentActivity
      members <- clusterMembers
      esStats <- elasticsSeachStats
    } yield {
      esStats map (
        ElasticSearchASG(asg, states.headOption, activities, members, _)
      ) getOrElse
        WebAppASG(asg, states.headOption, activities, members)
    }
  }
}

object FutureOption {
  def apply[T](of: Option[Future[T]]): Future[Option[T]] =
    of.map(f => f.map(Some(_))) getOrElse (Future.successful(None))
}

