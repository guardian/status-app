package model

import com.amazonaws.services.elasticloadbalancing.model.{DescribeInstanceHealthRequest, InstanceState}
import lib.{AmazonConnection, AWS}

import collection.convert.wrapAsScala._
import scala.concurrent.{ExecutionContext, Future}

case class ELB(name: String, instanceStates: List[InstanceState]) {
  lazy val members = instanceStates.map(new Member(_))

  class Member(instanceState: InstanceState) {
    def id = instanceState.getInstanceId
    def state = instanceState.getState
    def description = Option(instanceState.getDescription).filter(_ != "N/A")
    def reasonCode = Option(instanceState.getReasonCode).filter(_ != "N/A")
  }
}

object ELB {
  import ExecutionContext.Implicits.global

  def apply(lbName: String)(implicit conn: AmazonConnection): Future[ELB] = for {
    elbHealths <- AWS.futureOf(conn.elb.describeInstanceHealthAsync, new DescribeInstanceHealthRequest(lbName))
  } yield ELB(lbName, elbHealths.getInstanceStates.toList)
}
