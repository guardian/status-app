package model

import scala.concurrent.Future
import lib.{AWS, AmazonConnection}
import scala.concurrent.ExecutionContext.Implicits.global

import com.amazonaws.services.autoscaling.model.{Instance => AwsAsgInstance, _}

import collection.JavaConversions._
import scala.util.Try

case class ASG(asg: AutoScalingGroup, elb: Option[ELB], recentActivity: Seq[ScalingAction], members: Seq[ClusterMember]) {

  lazy val name = asg.getAutoScalingGroupName
  lazy val tags = asg.getTags.map(t => t.getKey -> t.getValue).toMap.withDefaultValue("")

  lazy val status = asg.getStatus

  lazy val stage = tags("Stage")
  lazy val appName = (tags get "Role") orElse (tags get "App") getOrElse "?"

  lazy val hasElb = elb.isDefined

  lazy val suspendedActivities = asg.getSuspendedProcesses.toList.map(_.getProcessName).sorted

  lazy val approxMonthlyCost =
    Try(members.map(_.instance.approxMonthlyCost).sum).getOrElse(BigDecimal(0))
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

  def all(implicit conn: AmazonConnection): Future[Seq[ASG]] =
    for {
      groups <- AWS.futureOf(conn.autoscaling.describeAutoScalingGroupsAsync, new DescribeAutoScalingGroupsRequest)
      asgs <- Future.sequence(groups.getAutoScalingGroups map (ASG(_)))
    } yield asgs

  def apply(name: String)(implicit conn: AmazonConnection): Future[ASG] =
    for {
      result <- AWS.futureOf(conn.autoscaling.describeAutoScalingGroupsAsync, new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(name))
      asg <- ASG(result.getAutoScalingGroups.head)
    } yield asg

  def apply(asg: AutoScalingGroup)(implicit conn: AmazonConnection): Future[ASG] =  {
    val instanceStates = Future.sequence((asg.getLoadBalancerNames.headOption map (ELB(_))).toSeq)

    val recentActivity = for {
      actions <- ScalingAction.forGroup(asg.getAutoScalingGroupName)
    } yield actions filter (_.isRecent)

    val clusterMembers = for {
      elb <- instanceStates
      members <- Future.sequence(asg.getInstances map {m =>
        val membersOfElb = elb.headOption.map(_.members).getOrElse(Nil)
        for {
          i <- Instance.get(m.getInstanceId)
        } yield new ClusterMember(m, membersOfElb.find(_.id == m.getInstanceId), i)
      })
    } yield members

    for {
      states <- instanceStates
      activities <- recentActivity
      members <- clusterMembers
    } yield ASG(asg, states.headOption, activities, members)
  }
}

