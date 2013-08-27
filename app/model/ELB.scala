package model

import com.amazonaws.services.elasticloadbalancing.model.{DescribeInstanceHealthRequest, InstanceState}
import lib.{AmazonConnection, AWS}

import collection.convert.wrapAsScala._
import scala.concurrent.{ExecutionContext, Future}
import com.amazonaws.services.cloudwatch.model.{Datapoint, Dimension, GetMetricStatisticsRequest}
import org.joda.time.DateTime

case class ELB(name: String, instanceStates: List[InstanceState], latency: Seq[Datapoint], requestCount: Seq[Datapoint]) {
  lazy val members = instanceStates.map(new ELBMember(_))

  lazy val active = requestCount.nonEmpty && requestCount.map(_.getSum).max > 10
}

case class ELBMember(instanceState: InstanceState) {
  def id = instanceState.getInstanceId
  def state = instanceState.getState
  def description = Option(instanceState.getDescription).filter(_ != "N/A")
  def reasonCode = Option(instanceState.getReasonCode).filter(_ != "N/A")
}

object ELB {
  import ExecutionContext.Implicits.global

  def avgLatency(lbName: String)(implicit conn: AmazonConnection) =
    AWS.futureOf(conn.cloudWatch.getMetricStatisticsAsync, new GetMetricStatisticsRequest()
      .withDimensions(new Dimension().withName("LoadBalancerName").withValue(lbName))
      .withMetricName("Latency").withNamespace("AWS/ELB").withPeriod(60).withStatistics("Average")
      .withStartTime(DateTime.now().minusHours(3).toDate).withEndTime(DateTime.now().toDate)
    )

  def requestCount(lbName: String)(implicit conn: AmazonConnection) =
    AWS.futureOf(conn.cloudWatch.getMetricStatisticsAsync, new GetMetricStatisticsRequest()
      .withDimensions(new Dimension().withName("LoadBalancerName").withValue(lbName))
      .withMetricName("RequestCount").withNamespace("AWS/ELB").withPeriod(60).withStatistics("Sum")
      .withStartTime(DateTime.now().minusHours(3).toDate).withEndTime(DateTime.now().toDate)
    )

  def apply(lbName: String)(implicit conn: AmazonConnection): Future[ELB] = for {
    elbHealths <- AWS.futureOf(conn.elb.describeInstanceHealthAsync, new DescribeInstanceHealthRequest(lbName))
    avgLatency <- avgLatency(lbName)
    requestCount <- requestCount(lbName)
  } yield ELB(lbName, elbHealths.getInstanceStates.toList,
      avgLatency.getDatapoints.sortBy(_.getTimestamp), requestCount.getDatapoints.sortBy(_.getTimestamp))
}
