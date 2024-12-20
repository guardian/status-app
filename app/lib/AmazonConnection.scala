package lib

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.autoscaling.AmazonAutoScalingAsyncClient
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient
import com.amazonaws.services.cloudwatch.model.Datapoint
import com.amazonaws.services.ec2.AmazonEC2AsyncClient
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingAsyncClient
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.sqs.AmazonSQSAsyncClient
import com.amazonaws.{AmazonWebServiceRequest, ClientConfiguration}
import model.ScalingAction
import play.api.libs.json.{Json, Writes}

import java.util.concurrent.{Future => JuncFuture}
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

class AmazonConnection(clientConfig: ClientConfiguration) {
  val credentialsProvider =  new DefaultAWSCredentialsProviderChain()

  val ec2 = new AmazonEC2AsyncClient(credentialsProvider, clientConfig)
  val elb = new AmazonElasticLoadBalancingAsyncClient(credentialsProvider, clientConfig)
  val autoscaling = new AmazonAutoScalingAsyncClient(credentialsProvider, clientConfig)
  val s3 = new AmazonS3Client(credentialsProvider, clientConfig)
  val cloudWatch: AmazonCloudWatchAsyncClient = Region.getRegion(Regions.EU_WEST_1).createClient(
    classOf[AmazonCloudWatchAsyncClient], credentialsProvider, clientConfig)
  val sqs = Region.getRegion(Regions.EU_WEST_1).createClient(
    classOf[AmazonSQSAsyncClient], credentialsProvider, clientConfig)

  ec2.setEndpoint("ec2.eu-west-1.amazonaws.com")
  elb.setEndpoint("elasticloadbalancing.eu-west-1.amazonaws.com")
  autoscaling.setEndpoint("autoscaling.eu-west-1.amazonaws.com")
}

object AWS {
  def futureOf[X <: AmazonWebServiceRequest, T](call: (X,AsyncHandler[X, T]) => JuncFuture[T], req: X): Future[T] = {
    val p = Promise[T]()
    val h = new AsyncHandler[X, T] {
      def onError(exception: Exception) { p.complete(Failure(exception)) }
      def onSuccess(request: X, result: T) { p.complete(Success(result)) }
    }
    call(req, h)
    p.future
  }

  lazy val connection = new AmazonConnection(Config.clientConfiguration)

  object Writes {
    implicit val datapointWrites = new Writes[Datapoint] {
      override def writes(d: Datapoint) = Json.obj(
        "time" -> d.getTimestamp.getTime,
        "average" -> Option(d.getAverage).map(_.toInt),
        "maximum" -> Option(d.getMaximum).map(_.toInt),
        "sum" -> Option(d.getSum).map(_.toLong)
      )
    }

    implicit val scalingActionWrites = new Writes[ScalingAction] {
      override def writes(a: ScalingAction) = Json.obj(
        "age" -> a.age,
        "cause" -> a.cause
      )
    }
  }
}
