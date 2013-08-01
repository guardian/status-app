package model

import com.amazonaws.services.elasticloadbalancing.model.InstanceState

import collection.JavaConversions._

import lib.AmazonConnection
import play.api.Logger
import util.Try
import scala.concurrent.{ExecutionContext, Future}

import ExecutionContext.Implicits.global


case class ELB(name: String, instanceStates: List[InstanceState]) {
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
case class Cluster(val asg: ASG, members: List[ClusterMember]) {
  def name = asg.name
  def appName = asg.app
  def stage = asg.stage

  lazy val hasElb = asg.elb.isDefined

  lazy val approxMonthlyCost =
    Try(members.map(_.instance.approxMonthlyCost).sum).getOrElse(BigDecimal(0))

}
case class ClusterMember(asgInfo: ASGMember, elbInfo: Option[ELB#Member], instance: Instance) {
  def id = asgInfo.id
  def healthStatus = asgInfo.healthStatus
  def lifecycleState = asgInfo.lifecycleState

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

object Cluster {
  val logger = Logger(getClass)

  def findAll(implicit aws: AmazonConnection): Future[Seq[Cluster]] = {
    logger.info("find all clusters")

    for {
      asgs <- ASG.all
      clusters <- Future.sequence(asgs.map(Cluster(_)))
    } yield clusters.sortBy(c => c.stage + c.appName)
  }

  def find(name: String)(implicit aws: AmazonConnection) =
    ASG(name) map (Cluster(_))

  def apply(asg: ASG)(implicit aws: AmazonConnection): Future[Cluster] = {
    val membersOfElb = asg.elb.map(_.members).getOrElse(Nil)

    for {
      members <- Future.sequence(asg.members map (m =>
        for {
          i <- Instance.get(m.id)
        } yield new ClusterMember(m, membersOfElb.find(_.id == m.id), i)
      ))
    } yield Cluster(asg, members)
  }
}
