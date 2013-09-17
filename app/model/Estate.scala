package model

import lib.{Config, AmazonConnection, AWS, ScheduledAgent}
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import scala.concurrent.Future
import controllers.Application
import com.amazonaws.services.sqs.model.ListQueuesRequest

trait Estate extends Map[String, Seq[ASG]] {
  def populated: Boolean
  def stageNames: Iterable[String]
  def queues: Seq[Queue]
  def -(key: String) = throw new UnsupportedOperationException
  def +[B1 >: Seq[ASG]](kv: (String, B1)) = throw new UnsupportedOperationException
  def asgs: Seq[ASG] = values.flatten.toSeq
}

case class PopulatedEstate(override val asgs: Seq[ASG], queues: Seq[Queue]) extends Estate {
  lazy val stageMap = asgs.groupBy(_.stage)
  def get(key: String) = stageMap.get(key)
  def iterator = stageMap.iterator

  lazy val stageNames = stageMap.keys.toSeq.sorted.sortWith((a, _) => if (a == "PROD") true else false)

  def populated = true
}

case object PendingEstate extends Estate {
  def get(key: String) = None
  def iterator = Nil.iterator
  def stageNames = Nil
  def populated = false
  def queues = Nil
}

object Estate {
  import scala.concurrent.duration._
  import scala.concurrent.ExecutionContext.Implicits.global
  import collection.convert.wrapAsScala._

  implicit val conn = AWS.connection

  val estateAgent = ScheduledAgent[Estate](0.seconds, 10.seconds, PendingEstate) {
    for {
      groups <- AWS.futureOf(conn.autoscaling.describeAutoScalingGroupsAsync, new DescribeAutoScalingGroupsRequest)
      asgs <- Future.traverse(groups.getAutoScalingGroups.toSeq)(ASG(_))
      queueResult <- AWS.futureOf(conn.sqs.listQueuesAsync, new ListQueuesRequest())
      queues <- Future.traverse(queueResult.getQueueUrls.toSeq)(Queue(_))
    } yield PopulatedEstate(asgs, queues)
  }
  def apply() = estateAgent()
}
