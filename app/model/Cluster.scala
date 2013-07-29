package model

import com.amazonaws.services.elasticloadbalancing.model.InstanceState

import collection.JavaConversions._

import lib.AmazonConnection
import play.api.Logger
import util.Try


class ELB(val name: String, instanceStates: List[InstanceState]) {
  lazy val members = instanceStates.map(new Member(_))

  class Member(instanceState: InstanceState) {
    def id = instanceState.getInstanceId
    def state = instanceState.getState
    def description = Option(instanceState.getDescription).filter(_ != "N/A")
    def reasonCode = Option(instanceState.getReasonCode).filter(_ != "N/A")
  }
}




// right now i'm thinking that Cluster === ASG - this class exists becuase I'm
// not totally convinced that is always true
class Cluster(val asg: ASG)(implicit awsConn: AmazonConnection) {
  def name = asg.name
  def appName = asg.app
  def stage = asg.stage

  lazy val hasElb = asg.elb.isDefined

  lazy val members: List[Member] = {
    val membersOfElb = asg.elb.map(_.members).getOrElse(Nil)

    asg.members.map(m =>
      new Member(m, membersOfElb.find(_.id == m.id))
    )
  }

  def refresh() = Cluster(asg.refresh())

  lazy val approxMonthlyCost =
    Try(members.map(_.instance.approxMonthlyCost).sum).getOrElse(BigDecimal(0))

  class Member(asgInfo: asg.Member, elbInfo: Option[ELB#Member]) {
    def id = asgInfo.id
    def healthStatus = asgInfo.healthStatus
    def lifecycleState = asgInfo.lifecycleState

    def state = elbInfo.map(_.state)
    def description = elbInfo.flatMap(_.description)
    def reasonCode = elbInfo.flatMap(_.reasonCode)

    def goodorbad = (healthStatus, lifecycleState, state) match {
      case (_, "Pending", _) | (_, "Terminating", _) => "pending"
      case ("Healthy", "InService", Some("InService")) => "good"
      case ("Healthy", "InService", None) => "good"
      case _ => "bad"
    }

    lazy val instance = model.Instance.get(id)
  }
}

object Cluster {
  val logger = Logger(getClass)

  def findAll(implicit aws: AmazonConnection): List[Cluster] = {
    logger.info("find all clusters")

    val groups = aws.autoscaling.describeAutoScalingGroups().getAutoScalingGroups.toList
      .map(new ASG(_))
      .map(new Cluster(_))
      .sortBy(c => c.stage + c.appName)

      groups.foreach{ cluster => logger.info("Cluster: "+cluster.stage+" - "+cluster.appName)}
    groups
  }

  def find(name: String)(implicit aws: AmazonConnection) =
    Cluster.findAll.filter(_.name == name).headOption

  def apply(asg: ASG)(implicit aws: AmazonConnection) = {
    new Cluster(asg)
  }
}
