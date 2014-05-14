package model

import com.amazonaws.services.elasticloadbalancing.model.{DescribeInstanceHealthRequest, InstanceState}
import lib.{AmazonConnection, AWS}
import com.amazonaws.services.cloudwatch.model.Statistic._

import collection.convert.wrapAsScala._
import scala.concurrent.{ExecutionContext, Future}
import com.amazonaws.services.cloudwatch.model.{Datapoint, Dimension, GetMetricStatisticsRequest}
import org.joda.time.DateTime
import play.api.libs.json.Json

case class ELB(name: String, members: List[ELBMember], latency: Seq[Datapoint], active: Boolean)
case class ELBMember(id: String, state: String, description: Option[String], reasonCode: Option[String])

object ELB {
  import ExecutionContext.Implicits.global

  def latency(lbName: String)(implicit conn: AmazonConnection) =
    AWS.futureOf(conn.cloudWatch.getMetricStatisticsAsync, new GetMetricStatisticsRequest()
      .withDimensions(new Dimension().withName("LoadBalancerName").withValue(lbName))
      .withMetricName("Latency").withNamespace("AWS/ELB").withPeriod(60)
      .withStatistics(Average, Maximum)
      .withStartTime(DateTime.now().minusHours(3).toDate).withEndTime(DateTime.now().toDate)
    )

  def requestCount(lbName: String)(implicit conn: AmazonConnection) =
    AWS.futureOf(conn.cloudWatch.getMetricStatisticsAsync, new GetMetricStatisticsRequest()
      .withDimensions(new Dimension().withName("LoadBalancerName").withValue(lbName))
      .withMetricName("RequestCount").withNamespace("AWS/ELB").withPeriod(60).withStatistics(Sum)
      .withStartTime(DateTime.now().minusHours(3).toDate).withEndTime(DateTime.now().toDate)
    )

  def forName(lbName: String)(implicit conn: AmazonConnection): Future[ELB] = for {
    elbHealths <- AWS.futureOf(conn.elb.describeInstanceHealthAsync, new DescribeInstanceHealthRequest(lbName))
    latency <- latency(lbName)
    requestCount <- requestCount(lbName)
  } yield {
    val members = elbHealths.getInstanceStates.toList.map(i => ELBMember(
      i.getInstanceId, i.getState,
      Option(i.getDescription).filter(_ != "N/A"),
      Option(i.getReasonCode).filter(_ != "N/A")
    ))
    val active = requestCount.getDatapoints.nonEmpty && requestCount.getDatapoints.map(_.getSum).max > 10
    val latencyInMs = latency.getDatapoints.sortBy(_.getTimestamp).map(p =>
      new Datapoint().withTimestamp(p.getTimestamp).withAverage(p.getAverage * 1000)
    )

    ELB(lbName, members, latencyInMs, active)
  }

  import AWS.Writes._
  implicit val elbMemberWrites = Json.writes[ELBMember]
  implicit val elbWrites = Json.writes[ELB]
}
