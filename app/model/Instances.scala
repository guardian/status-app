package model

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.ec2.model.{Instance => AwsInstance, DescribeInstancesResult, DescribeInstancesRequest}
import lib.{ScheduledAgent, AWS, AmazonConnection}
import play.api.Logger
import collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
import scala.concurrent.duration._

object Instances {

  private val log = Logger[Instance](classOf[Instance])

  private val agent = ScheduledAgent[Seq[Instance]](0.seconds, 1.minute, Seq.empty) {
    def describeInstanceRequest(nextToken: Option[String]) =
      nextToken map { new DescribeInstancesRequest().withNextToken } getOrElse { new DescribeInstancesRequest() }

    def withAwsInstances[T](token: Option[String] = None)(fn: AwsInstance => T): Future[Seq[T]] = {
      AWS.futureOf(AWS.connection.ec2.describeInstancesAsync, describeInstanceRequest(token)) flatMap { result =>
        val nextToken = Option(result.getNextToken)
        if (nextToken.isDefined)
          withAwsInstances(nextToken)(fn)
        else
          Future.successful(result.getReservations.flatMap(_.getInstances).map(fn))
      } recover {
        case e =>
          log.error(s"Unable to retrieve all instances", e)
          Seq.empty
      }
    }

    val instances = withAwsInstances() { instance =>
      Instance.from(instance) recover {
        case _ => UnknownInstance(instance.getInstanceId)
      }
    }
    instances.flatMap(Future.sequence(_))
  }

  def apply(): Seq[Instance] = agent.get()

  def getById(id: String): Option[Instance] = agent.get().find(_.id == id)
}