package model

import com.amazonaws.services.elasticloadbalancing.model.{DescribeInstanceHealthRequest, InstanceState}
import lib.{AmazonConnection, AWS}
import com.amazonaws.services.cloudwatch.model.Statistic._

import collection.convert.wrapAsScala._
import scala.concurrent.{ExecutionContext, Future}
import com.amazonaws.services.cloudwatch.model.{Statistic, Datapoint, Dimension, GetMetricStatisticsRequest}
import org.joda.time.DateTime
import play.api.libs.json.Json
import scala.annotation.tailrec

case class ELB(name: String, members: List[ELBMember], latency: Seq[Datapoint], errorCount: Seq[Datapoint], active: Boolean)
case class ELBMember(id: String, state: String, description: Option[String], reasonCode: Option[String])
object ELB {
  import AWS.Writes._
  implicit val elbMemberWrites = Json.writes[ELBMember]
  implicit val elbWrites = Json.writes[ELB]

  import ExecutionContext.Implicits.global

  val timeSpan: DateTime => DateTime = _.minusHours(3)

  def statistic(lbName: String)(metricName: String, statistics: Statistic*)(implicit conn: AmazonConnection) =
    AWS.futureOf(conn.cloudWatch.getMetricStatisticsAsync, new GetMetricStatisticsRequest()
      .withDimensions(new Dimension().withName("LoadBalancerName").withValue(lbName))
      .withMetricName(metricName).withNamespace("AWS/ELB").withPeriod(60)
      .withStatistics(statistics: _*)
      .withStartTime(timeSpan(DateTime.now()).toDate).withEndTime(DateTime.now().toDate)
    )

  def latency(lbName: String)(implicit conn: AmazonConnection) =
    statistic(lbName)("Latency", Average, Maximum)

  def requestCount(lbName: String)(implicit conn: AmazonConnection) =
    statistic(lbName)("RequestCount", Sum)

  def errorCount(lbName: String)(implicit conn: AmazonConnection) =
    statistic(lbName)("HTTPCode_Backend_5XX", Sum)

  def forName(lbName: String)(implicit conn: AmazonConnection): Future[ELB] = for {
    elbHealths <- AWS.futureOf(conn.elb.describeInstanceHealthAsync, new DescribeInstanceHealthRequest(lbName))
    latency <- latency(lbName)
    requestCount <- requestCount(lbName)
    errorCount <- errorCount(lbName)
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

    val start = extremeTime(latencyInMs)(_.minBy(_.getTimestamp))
    val end = extremeTime(latencyInMs)(_.maxBy(_.getTimestamp))

    ELB(lbName, members, latencyInMs, zeroFillPerMinute(start, end)(errorCount.getDatapoints), active)
  }



  def extremeTime(series: Seq[Datapoint])(f: Seq[Datapoint] => Datapoint): DateTime =
    if (series.isEmpty) DateTime.now().withSecondOfMinute(0).withMillisOfSecond(0)
    else new DateTime(f(series).getTimestamp)

  def perMinute(from: DateTime, to: DateTime): Seq[DateTime] = {
    @tailrec
    def perMin(acc: Seq[DateTime], from: DateTime): Seq[DateTime] = {
      if (from.isAfter(to)) acc
      else perMin(from +: acc, from.plusMinutes(1))
    }

    perMin(Nil, from).reverse
  }

  private def zeroFillPerMinute(from: DateTime, to: DateTime)(line: Seq[Datapoint]): Seq[Datapoint] = {
    def zeroDatapoint(t: DateTime) = new Datapoint().withAverage(0).withMaximum(0).withMinimum(0)
      .withSampleCount(0).withSum(0).withTimestamp(t.toDate)

    val dataMap = line.map(p => new DateTime(p.getTimestamp) -> p).toMap

    for (t <- perMinute(from, to)) yield {
      dataMap.getOrElse(t, zeroDatapoint(t))
    }
  }
}
