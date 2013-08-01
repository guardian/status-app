package model

import scala.concurrent.Future
import lib.{AWS, UptimeDisplay, AmazonConnection}
import org.joda.time.{Duration, DateTime}
import scala.concurrent.ExecutionContext.Implicits.global

import com.amazonaws.services.autoscaling.model.{Instance => AwsAsgInstance, _}
import com.amazonaws.services.elasticloadbalancing.model.{Instance => AwsElbInstance, DescribeInstanceHealthRequest}

import collection.JavaConversions._

class ScalingAction(a: Activity) {
  def startTime = new DateTime(a.getStartTime)
  def ageMins = new Duration(startTime, DateTime.now).getStandardMinutes
  def isRecent = ageMins < 60

  def age = UptimeDisplay.print(startTime) + " ago"
  def cause = a.getCause
}

case class ASG(asg: AutoScalingGroup, elb: Option[ELB], recentActivity: Seq[ScalingAction]) {

  lazy val name = asg.getAutoScalingGroupName
  lazy val members = asg.getInstances.toList.map(ASGMember(_))
  lazy val tags = asg.getTags.map(t => t.getKey -> t.getValue).toMap.withDefaultValue("")

  lazy val status = asg.getStatus

  lazy val stage = tags("Stage")
  lazy val app = (tags get "Role") orElse (tags get "App") getOrElse "?"

  lazy val suspendedActivities = asg.getSuspendedProcesses.toList.map(_.getProcessName).sorted
}

case class ASGMember(instance: AwsAsgInstance) {
  def id = instance.getInstanceId
  def healthStatus = instance.getHealthStatus
  def lifecycleState = instance.getLifecycleState
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
    val instanceStates = Future.sequence((asg.getLoadBalancerNames.headOption map { lbName =>
      for {
        elbHealths <- AWS.futureOf(conn.elb.describeInstanceHealthAsync, new DescribeInstanceHealthRequest(lbName))
      } yield ELB(lbName, elbHealths.getInstanceStates.toList)
    }).toSeq)

    val recentActivity = for {
      activities <- AWS.futureOf(conn.autoscaling.describeScalingActivitiesAsync,
        new DescribeScalingActivitiesRequest().withAutoScalingGroupName(asg.getAutoScalingGroupName))
    } yield activities.getActivities map (new ScalingAction(_)) filter (_.isRecent)

    for {
      states <- instanceStates
      activities <- recentActivity
    } yield ASG(asg, states.headOption, activities)
  }
}

