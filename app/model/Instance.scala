package model

import com.amazonaws.services.ec2.model.{Instance => AwsEc2Instance, DescribeInstancesRequest}
import lib._
import collection.JavaConversions._
import play.api.libs.ws.{Response, WS}
import play.api.cache.Cache
import scala.concurrent.ExecutionContext.Implicits.global
import concurrent.{Promise, Future, Await}
import scala.concurrent.duration._
import util.Try
import play.api.libs.ws.Response
import scala.Some
import lib.EC2CostingType

class Instance(awsInstance: AwsEc2Instance) {
  def id = awsInstance.getInstanceId
  def publicDns = awsInstance.getPublicDnsName
  def publicIpAddress = awsInstance.getPublicIpAddress
  def privateDns = awsInstance.getPrivateDnsName
  def privateIpAddress = awsInstance.getPrivateIpAddress
  def instanceType = awsInstance.getInstanceType

  def availabilityZone = awsInstance.getPlacement.getAvailabilityZone

  def cost = AWSCost(costingType)

  def approxMonthlyCost = cost * 24 * 30

  def launched = awsInstance.getLaunchTime

  def uptime = UptimeDisplay.print(launched)

  lazy val tags = awsInstance.getTags.map(t => t.getKey -> t.getValue).toMap.withDefaultValue("")

  lazy val stage = tags("Stage")
  lazy val app = tags("Role")

  def usefulUrls: List[(String, String)] = Nil

  protected def versionFuture: Future[Option[String]] =
    Promise.successful(None).future

  final def version: Option[String] =
    Try(Await.result(versionFuture, 2.seconds))
      .recover { case e => Some(e.getMessage) }
      .get

  def costingType = EC2CostingType(awsInstance.getInstanceType, awsInstance.getPlacement.getAvailabilityZone)
}

class ElasticSearchInstance(awsInstance: AwsEc2Instance) extends Instance(awsInstance) {
  lazy val baseUrl = "http://%s:9200".format(publicDns)

  override def usefulUrls = List(
    "head" -> (baseUrl + "/_plugin/head/"),
    "bigdesk" -> (baseUrl + "/_plugin/bigdesk/"),
    "paramedic" -> (baseUrl + "/_plugin/paramedic/")
  )

  override val versionFuture =
    WS.url(baseUrl).get().map { r: Response =>
      val v = (r.json \ "version" \ "number").as[String]
      val name = (r.json \ "name").as[String]
      Some(v + " " + name)
    }
}

class StandardWebApp(awsInstance: AwsEc2Instance, port: Int = 8080) extends Instance(awsInstance) {
  lazy val manifestUrl = "http://%s:%d/management/manifest".format(publicDns, port)

  override def usefulUrls = List(
    "manifest" -> manifestUrl
  )

  override val versionFuture =
    WS.url(manifestUrl).get().map { r =>
      val values = r.body.lines.map(_.split(':').map(_.trim)).collect { case Array(k, v) => k -> v }.toMap
      values.get("Build")
    }
}

object Instance {
  import play.api.Play.current

  private def uncachedGet(id: String)(implicit awsConn: AmazonConnection) = {
    awsConn.ec2.describeInstances(new DescribeInstancesRequest().withInstanceIds(id))
      .getReservations
      .flatMap(_.getInstances)
      .map(specificInstanceType)
      .head
  }

  def get(id: String)(implicit awsConn: AmazonConnection) = {
    Cache.getOrElse(id, 30) {
      uncachedGet(id)
    }
  }

  private def specificInstanceType(i: AwsEc2Instance) = {
    val appTag = i.getTags.find(_.getKey == "Role").map(_.getValue)

    (for {
      role <- appTag if role.contains("elasticsearch")
    } yield new ElasticSearchInstance(i)) getOrElse new StandardWebApp(i, Config.managementPort.getOrElse(9000))
  }
}
