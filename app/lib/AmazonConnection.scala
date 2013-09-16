package lib

import com.amazonaws.auth.{DefaultAWSCredentialsProviderChain, AWSCredentialsProvider, AWSCredentials}
import com.amazonaws.services.ec2.AmazonEC2AsyncClient
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingAsyncClient
import com.amazonaws.services.autoscaling.AmazonAutoScalingAsyncClient
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.{AmazonWebServiceRequest, ClientConfiguration}
import com.amazonaws.handlers.AsyncHandler
import java.util.concurrent.{Future => JuncFuture}
import scala.concurrent.{Promise, Future}
import scala.util.{Success, Failure}
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient
import com.amazonaws.regions.{Regions, Region}

class AmazonConnection(credentials: Option[AWSCredentials], clientConfig: ClientConfiguration) {
  val credentialsProvider =  new AWSCredentialsProvider() {
    val defaultProvider = new DefaultAWSCredentialsProviderChain()
    def getCredentials = credentials.getOrElse(defaultProvider.getCredentials)

    def refresh() {
      defaultProvider.refresh()
    }
  }

  val ec2 = new AmazonEC2AsyncClient(credentialsProvider, clientConfig)
  val elb = new AmazonElasticLoadBalancingAsyncClient(credentialsProvider, clientConfig)
  val autoscaling = new AmazonAutoScalingAsyncClient(credentialsProvider, clientConfig)
  val s3 = new AmazonS3Client(credentialsProvider, clientConfig)
  val cloudWatch = Region.getRegion(Regions.EU_WEST_1).createClient(
    classOf[AmazonCloudWatchAsyncClient], credentialsProvider, new ClientConfiguration())

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
}
