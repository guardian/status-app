package model

import scala.concurrent.{ExecutionContext, Future}
import lib.{AWS, AmazonConnection}
import com.amazonaws.services.cloudwatch.model.{Datapoint, Dimension, GetMetricStatisticsRequest}
import org.joda.time.DateTime

import collection.convert.wrapAsScala._
import play.api.Logger

case class Queue(name: String, url: String, approxQueueLength: Seq[Datapoint])

object Queue {
  def from(url: String)(implicit conn: AmazonConnection, context: ExecutionContext): Future[Queue] = {
    val name = url.split("/").last
    for{
      stats <- AWS.futureOf(conn.cloudWatch.getMetricStatisticsAsync, new GetMetricStatisticsRequest()
        .withDimensions(new Dimension().withName("QueueName").withValue(name))
        .withMetricName("ApproximateNumberOfMessagesVisible").withNamespace("AWS/SQS").withPeriod(60).withStatistics("Average")
        .withStartTime(DateTime.now().minusHours(3).toDate).withEndTime(DateTime.now().toDate)
      )
    } yield Queue(name, url, stats.getDatapoints.sortBy(_.getTimestamp))
  }
}
