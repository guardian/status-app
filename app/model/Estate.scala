package model

import akka.actor.ActorSystem
import com.amazonaws.services.autoscaling.model.{AutoScalingGroup, DescribeAutoScalingGroupsRequest}
import com.amazonaws.services.ec2.model.{DescribeInstancesRequest, Instance => AwsEc2Instance}
import com.amazonaws.services.sqs.model.{ListQueuesRequest, ListQueuesResult}
import lib.{AWS, ScheduledAgent}
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.{Json, Writes}
import play.api.libs.ws.WSClient

import scala.collection.convert.wrapAll._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
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
    (asg.stage.getOrElse("unknown"), asg.stack.getOrElse("unknown"))
  ).toSeq.map { case ((stage, name), asgs) => stage -> Stack(name, asgs) }

  lazy val stages = stacks.foldLeft(Map.empty[String, Seq[Stack]].withDefaultValue(Seq[Stack]())){
    case (map, (stage, stack)) => map.updated(stage, map(stage) :+ stack)
  } mapValues (stacks => Stage(stacks.sortBy(- _.asgs.flatMap(_.members).length)))

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

class EstateProvider(
  asgSource: ASGSource
)(implicit wsClient: WSClient, actorSystem: ActorSystem) {
  val log = Logger(classOf[EstateProvider])
  implicit val conn = AWS.connection
  val estateAgent = ScheduledAgent[Estate](0.seconds, 30.seconds, PendingEstate) {
    log.info(s"Starting estateAgent")

    val instancesFuture: Future[List[AwsEc2Instance]] = EstateInstances.fetchAllInstances()
    val queuesFuture: Future[ListQueuesResult] = AWS.futureOf[ListQueuesRequest,ListQueuesResult](conn.sqs.listQueuesAsync, new ListQueuesRequest())

    for {
      instances <- instancesFuture
      nonTerminatedInstances = instances.filterNot(i => i.getState.getName=="terminated")
      tagsToInstances = EstateInstances.groupInstancesByTag(nonTerminatedInstances)
      asgs <- Future.traverse(tagsToInstances)({case(_, i) => asgSource.fromApp(i)})
      queueResult <- queuesFuture.recover {
        case NonFatal(e) => {
          log.logger.error("Error retrieving queues", e)
          new ListQueuesResult().withQueueUrls(Seq(s"/ERROR ${e.getMessage}"))
        }
      }
      queues <- Future.traverse(queueResult.getQueueUrls.toSeq)(Queue.from)
    } yield PopulatedEstate(asgs.seq.toList.flatten, queues, DateTime.now)
  }
  def apply(): Estate = estateAgent()
}

object EstateInstances {

  val log = Logger(classOf[Estate])

  implicit val conn = AWS.connection

  def fetchAsgByName(name: String): Future[Option[AutoScalingGroup]] = {
    val request = new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(name)
    AWS.futureOf(conn.autoscaling.describeAutoScalingGroupsAsync, request).map { result =>
      result.getAutoScalingGroups.headOption
    }
  }

  def fetchAllInstances(nextToken: Option[String] = None): Future[List[com.amazonaws.services.ec2.model.Instance]] = {
    val request = new DescribeInstancesRequest
    nextToken.foreach(request.setNextToken)
    AWS.futureOf(conn.ec2.describeInstancesAsync, request).flatMap { result =>
      val instances = result.getReservations().toList.flatMap(r => r.getInstances())
      Option(result.getNextToken()) match {
        case None => Future.successful(instances)
        case token: Some[String] => fetchAllInstances(token).map(_ ++ instances)
      }
    }
  }

  def groupInstancesByTag(instances: List[AwsEc2Instance]): Map[Map[String, String], List[AwsEc2Instance]] = {
    instances.groupBy(i => {
      i.getTags.toList.filter(t => t.getKey == "App" || t.getKey == "Stage" || t.getKey == "Stack")
        .map(t => t.getKey -> t.getValue).toMap
    })
  }


}
