package model

import lib.{Config, AmazonConnection, AWS, ScheduledAgent}
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import scala.concurrent.Future
import controllers.Application

trait Estate extends Map[String, Seq[ASG]] {
  def populated: Boolean
  def stages: Iterable[String]
  def -(key: String) = throw new UnsupportedOperationException
  def +[B1 >: Seq[ASG]](kv: (String, B1)) = throw new UnsupportedOperationException
  def asgs: Seq[ASG] = values.flatten.toSeq
}

case class PopulatedEstate(override val asgs: Seq[ASG]) extends Estate {
  lazy val stageMap = asgs.groupBy(_.stage)
  def get(key: String) = stageMap.get(key)
  def iterator = stageMap.iterator

  lazy val stages = stageMap.keys.toSeq.sorted.sortWith((a, _) => if (a == "PROD") true else false)

  def populated = true
}

case object PendingEstate extends Estate{
  def get(key: String) = None
  def iterator = Nil.iterator
  def stages = Nil
  def populated = false
}

object Estate {
  import scala.concurrent.duration._
  import scala.concurrent.ExecutionContext.Implicits.global
  import collection.convert.wrapAsScala._

  implicit val conn = AmazonConnection()

  val estateAgent = ScheduledAgent[Estate](0.seconds, 10.seconds, PendingEstate) {
    for {
      groups <- AWS.futureOf(conn.autoscaling.describeAutoScalingGroupsAsync, new DescribeAutoScalingGroupsRequest)
      asgs <- Future.sequence(groups.getAutoScalingGroups map (ASG(_)))
    } yield PopulatedEstate(asgs)
  }
  def apply() = estateAgent()
}

object AmazonConnection {
  def apply() = new AmazonConnection(Config.credentials, Config.clientConfiguration)
}
