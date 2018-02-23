package model

import com.amazonaws.services.ec2.model.{Instance => AwsEc2Instance, DescribeInstancesRequest}
import lib.{AWS, ScheduledAgent}
import com.amazonaws.services.autoscaling.model.{AutoScalingGroup, DescribeAutoScalingGroupsRequest}
import play.api.Logger
import scala.concurrent.Future
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

object Estate {
  import scala.concurrent.duration._
  import scala.concurrent.ExecutionContext.Implicits.global
  import collection.convert.wrapAll._

  import play.api.Play.current

  val log = Logger[Estate](classOf[Estate])

  implicit val conn = AWS.connection

  def fetchAsgByName(name: String): Future[Option[AutoScalingGroup]] = {
    val request = new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(name)
    AWS.futureOf(conn.autoscaling.describeAutoScalingGroupsAsync, request).map { result =>
      result.getAutoScalingGroups.headOption
    }
  }

  private def fetchAllInstances(nextToken: Option[String] = None): Future[List[com.amazonaws.services.ec2.model.Instance]] = {
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

  val estateAgent = ScheduledAgent[Estate](0.seconds, 30.seconds, PendingEstate) {
    val instancesFuture = fetchAllInstances()
    val queuesFuture = AWS.futureOf[ListQueuesRequest,ListQueuesResult](conn.sqs.listQueuesAsync, new ListQueuesRequest())

    for {
      instances <- instancesFuture
      nonTerminatedInstances = instances.filterNot(i => i.getState.getName=="terminated")
      tagsToInstances = groupInstancesByTag(nonTerminatedInstances)
      asgs <- Future.traverse(tagsToInstances)({case(_, instances) => ASG.fromApp(instances)})
      queueResult <- queuesFuture.recover {
        case NonFatal(e) => {
          log.logger.error("Error retrieving queues", e)
          new ListQueuesResult().withQueueUrls(Seq(s"/ERROR ${e.getMessage}"))
        }
      }
      queues <- Future.traverse(queueResult.getQueueUrls.toSeq)(Queue.from)
    } yield PopulatedEstate(asgs.seq.toList, queues, DateTime.now)
  }
  def apply() = estateAgent()
}
