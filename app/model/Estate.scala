package model

import com.amazonaws.AmazonServiceException
import lib.{Config, AmazonConnection, AWS, ScheduledAgent}
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import play.api.Logger
import scala.concurrent.Future
import controllers.Application
import com.amazonaws.services.sqs.model.{ListQueuesResult, ListQueuesRequest}
import org.joda.time.DateTime
import play.api.libs.json.{Writes, Json}

import scala.util.control.NonFatal

trait Estate extends Map[String, Stage] {
  def populated: Boolean
  def stageNames: Iterable[String]
  def queues: Seq[Queue]
  def -(key: String) = throw new UnsupportedOperationException
  def +[B1 >: Stage](kv: (String, B1)) = throw new UnsupportedOperationException
  def asgs: Seq[ASG] = values.flatMap(_.asgs).toSeq
  def lastUpdated: Option[DateTime]
}

case class Stack(name: String, asgs: Seq[ASG])
object Stack {
  implicit val writes = Json.writes[Stack]
}
case class Stage(stacks: Seq[Stack]) {
  def asgs: Seq[ASG] = stacks.flatMap(_.asgs)
}
object Stage {
  implicit val writes = new Writes[Stage] {
    def writes(stage: Stage) = Json.toJson(Map(stage.stacks.map(s => s.name -> s): _ *))
  }
}

case class PopulatedEstate(override val asgs: Seq[ASG], queues: Seq[Queue], lastUpdateTime: DateTime)
    extends Estate {
  lazy val stacks = asgs.groupBy(asg =>
    (asg.stage.getOrElse("unkown"), asg.stack.getOrElse("unknown"))
  ).toSeq.map { case ((stage, name), asgs) => stage -> Stack(name, asgs) }

  lazy val stages = stacks.foldLeft(Map.empty[String, Seq[Stack]].withDefaultValue(Seq[Stack]())){
    case (map, (stage, stack)) => map.updated(stage, map(stage) :+ stack)
  } mapValues (stacks => Stage(stacks.sortBy(_.name)))

  def get(key: String) = stages.get(key)
  def iterator = stages.iterator

  lazy val stageNames = stages.keys.toSeq.sorted.sortWith((a, _) => if (a == "PROD") true else false)

  def populated = true

  def lastUpdated = Some(lastUpdateTime)
}

case object PendingEstate extends Estate {
  def get(key: String) = None
  def iterator = Nil.iterator
  def stageNames = Nil
  def populated = false
  def queues = Nil
  def lastUpdated = None
}

object Estate {
  import scala.concurrent.duration._
  import scala.concurrent.ExecutionContext.Implicits.global
  import collection.convert.wrapAll._

  import play.api.Play.current

  val log = Logger[Estate](classOf[Estate])

  implicit val conn = AWS.connection

  val estateAgent = ScheduledAgent[Estate](0.seconds, 10.seconds, PendingEstate) {
    val groupsFuture = AWS.futureOf(conn.autoscaling.describeAutoScalingGroupsAsync, new DescribeAutoScalingGroupsRequest)
    val queuesFuture = AWS.futureOf(conn.sqs.listQueuesAsync, new ListQueuesRequest())

    for {
      groups <- groupsFuture
      asgs <- Future.traverse(groups.getAutoScalingGroups.toSeq)(ASG.from)
      queueResult <- queuesFuture.recover {
        case NonFatal(e) => {
          log.logger.error("Error retrieving queues", e)
          new ListQueuesResult().withQueueUrls(Seq(s"/ERROR ${e.getMessage}"))
        }
      }
      queues <- Future.traverse(queueResult.getQueueUrls.toSeq)(Queue.from)
    } yield PopulatedEstate(asgs, queues, DateTime.now)
  }
  def apply() = estateAgent()
}
