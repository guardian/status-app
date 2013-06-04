package model

import lib.{UptimeDisplay, AmazonConnection}
import play.api.libs.concurrent.Akka
import org.joda.time.{Duration, DateTime}
import scala.concurrent.ExecutionContext.Implicits.global


import com.amazonaws.services.autoscaling.model.{Instance => AwsAsgInstance, _}
import com.amazonaws.services.elasticloadbalancing.model.{Instance => AwsElbInstance, DeregisterInstancesFromLoadBalancerRequest, DescribeInstanceHealthRequest, InstanceState, LoadBalancerDescription}

import collection.JavaConversions._
import util.Try

class ASG(asg: AutoScalingGroup)(implicit awsConn: AmazonConnection) {
  import play.api.Play.current
  implicit val system = Akka.system

  lazy val name = asg.getAutoScalingGroupName
  lazy val members = asg.getInstances.toList.map(new Member(_))
  lazy val tags = asg.getTags.map(t => t.getKey -> t.getValue).toMap.withDefaultValue("")

  lazy val status = asg.getStatus

  lazy val stage = tags("Stage")
  lazy val app = (tags get "Role") orElse (tags get "App") getOrElse "?"

  lazy val suspendedActivities = asg.getSuspendedProcesses.toList.map(_.getProcessName).sorted
  lazy val elbNames = asg.getLoadBalancerNames

  lazy val recentActivity = Try {
    awsConn.autoscaling
      .describeScalingActivities(new DescribeScalingActivitiesRequest().withAutoScalingGroupName(name))
      .getActivities
      .map(new ScalingAction(_))
      .filter(_.isRecent)
  } getOrElse Nil

  lazy val desiredCapacity = asg.getDesiredCapacity
  def desiredCapacity(capacity: Int) = ASG.desiredCapacity(name, capacity)
  lazy val maxCapacity = asg.getMaxSize
  def maxCapacity(capacity: Int) = ASG.maxCapacity(name, capacity)

  def refresh() = ASG(name)

  class ScalingAction(a: Activity) {
    def startTime = new DateTime(a.getStartTime)
    def ageMins = new Duration(startTime, DateTime.now).getStandardMinutes
    def isRecent = ageMins < 60

    def age = UptimeDisplay.print(startTime) + " ago"
    def cause = a.getCause
  }

  def elbHealth = elbNames
    .map(n => awsConn.elb.describeInstanceHealth(new DescribeInstanceHealthRequest(n)).getInstanceStates)

  lazy val elbs = elbNames zip elbHealth map { case (elbName, health) =>
    new ELB(elbName, health.toList)
  }

  // we don't have any cases at the moment with more than one elb, so make this obvious
  lazy val elb = elbs.headOption

  class Member(instance: AwsAsgInstance) {
    def id = instance.getInstanceId
    def healthStatus = instance.getHealthStatus
    def lifecycleState = instance.getLifecycleState

    def terminate() = {
      awsConn.autoscaling.terminateInstanceInAutoScalingGroup(
        new TerminateInstanceInAutoScalingGroupRequest().withInstanceId(id).withShouldDecrementDesiredCapacity(true)
      )
    }
  }
}

object ASG {
  def suspend(name: String)(implicit conn: AmazonConnection) = {
    conn.autoscaling.suspendProcesses(new SuspendProcessesRequest().withAutoScalingGroupName(name))
    ASG(name)
  }
  def resume(name: String)(implicit conn: AmazonConnection) = {
    conn.autoscaling.resumeProcesses(new ResumeProcessesRequest().withAutoScalingGroupName(name))
    ASG(name)
  }

  def desiredCapacity(name: String, capacity: Int)(implicit conn: AmazonConnection) = {
    conn.autoscaling.setDesiredCapacity(
      new SetDesiredCapacityRequest().withAutoScalingGroupName(name).withDesiredCapacity(capacity))
    ASG(name)
  }

  def maxCapacity(name: String, capacity: Int)(implicit conn: AmazonConnection) = {
    conn.autoscaling.updateAutoScalingGroup(
      new UpdateAutoScalingGroupRequest().withAutoScalingGroupName(name).withMaxSize(capacity))
    ASG(name)
  }

  def apply(name: String)(implicit conn: AmazonConnection) =
    new ASG(conn.autoscaling.describeAutoScalingGroups(
      new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(name)
    ).getAutoScalingGroups.head)
}

