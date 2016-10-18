package model

import com.amazonaws.services.cloudwatch.model.Statistic._
import com.amazonaws.services.cloudwatch.model.{Datapoint, Dimension, GetMetricStatisticsRequest, GetMetricStatisticsResult}
import lib.AWS
import org.joda.time.DateTime
import play.api.libs.json.Json
import scala.concurrent.ExecutionContext.Implicits.global
import collection.JavaConversions._

import scala.concurrent.Future

object Billing {

  val services = List("AmazonS3", "AmazonEC2", "ElasticMapReduce", "all")

  def last24hrCostByService(service: String):Future[GetMetricStatisticsResult] = {
    implicit val conn = AWS.connection
    val metricReq =  new GetMetricStatisticsRequest()
      .withMetricName("EstimatedCharges").withNamespace("AWS/Billing").withPeriod(24 * 60 * 60)
      .withDimensions(new Dimension().withName("Currency").withValue("USD"))
      .withStatistics(Maximum)
      .withStartTime(DateTime.now().minusHours(60).toDate).withEndTime(DateTime.now().toDate)
    val withService = if(service=="all") metricReq else metricReq.withDimensions(new Dimension().withName("ServiceName").withValue(service))
    AWS.futureOf(conn.billingWatch.getMetricStatisticsAsync, withService)
  }

  def parseBillingData(res: GetMetricStatisticsResult, service: String): Option[BillEstimate] = {
    val datapoints = res.getDatapoints.sortBy(_.getTimestamp).toList
    (for {
      first <- datapoints.headOption
      second <- datapoints.drop(1).headOption
      cost =  second.getMaximum() - first.getMaximum()
    } yield (BillEstimate(first.getTimestamp().toString, second.getTimestamp().toString, service, cost)))
  }

  def billEstimatesByService: Future[List[BillEstimate]] = {
    Future.sequence {
      services.map(s =>
        last24hrCostByService(s).map(res => parseBillingData(res,s))
      )
    }.map(_.flatten)
  }
}

case class BillEstimate(from: String, to: String, service: String, cost: Double)

case class ServiceToCost(from: String, to: String, cost: Map[String, Double])

object ServiceToCost {

  implicit val jsonFormat = Json.format[ServiceToCost]

  def fromBillEst(billEst: List[BillEstimate]): List[ServiceToCost] = {
    billEst.groupBy(b => (b.from, b.to)).map { case ((from, to), bills) =>
      ServiceToCost(from, to, bills.map(b => b.service -> b.cost).toMap)
    }.toList
  }
}
