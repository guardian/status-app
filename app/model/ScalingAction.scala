package model

import com.amazonaws.services.autoscaling.model.{DescribeScalingActivitiesRequest, Activity}
import org.joda.time.{Duration, DateTime}
import lib.{AmazonConnection, AWS, UptimeDisplay}

import collection.convert.wrapAsScala._
import scala.concurrent.{ExecutionContext, Future}

case class ScalingAction(a: Activity) {
  def startTime = new DateTime(a.getStartTime)
  def ageMins = new Duration(startTime, DateTime.now).getStandardMinutes
  def isRecent = ageMins < 60

  def age = UptimeDisplay.print(startTime) + " ago"
  def cause = a.getCause
}

object ScalingAction {
  import ExecutionContext.Implicits.global

  def forGroup(asgName: String)(implicit conn: AmazonConnection): Future[Seq[ScalingAction]] = {
    for {
      activities <- AWS.futureOf(conn.autoscaling.describeScalingActivitiesAsync,
        new DescribeScalingActivitiesRequest().withAutoScalingGroupName(asgName))
    } yield activities.getActivities map (new ScalingAction(_))
  }
}
