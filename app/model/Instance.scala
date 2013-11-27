package model

import com.amazonaws.services.ec2.model.{Instance => AwsEc2Instance, DescribeInstancesRequest}
import lib._
import collection.JavaConversions._
import play.api.libs.ws.WS
import play.api.cache.Cache
import scala.concurrent.ExecutionContext.Implicits.global
import concurrent.Future
import play.api.libs.ws.Response
import scala.Some
import play.api.Logger
import scala.util.Try

case class Instance(awsInstance: AwsEc2Instance, version: Option[String], usefulUrls: Seq[(String, String)]) {
  def id = awsInstance.getInstanceId
  def publicDns = awsInstance.getPublicDnsName
  def publicIp = awsInstance.getPublicIpAddress
  def privateDns = awsInstance.getPrivateDnsName
  def privateIp = awsInstance.getPrivateIpAddress
  def instanceType = awsInstance.getInstanceType

  def availabilityZone = awsInstance.getPlacement.getAvailabilityZone

  def cost = Try(AWSCost(costingType)).getOrElse(BigDecimal(0))

  def approxMonthlyCost = cost * 24 * 30

  def costingType = EC2CostingType(instanceType, availabilityZone)

  def launched = awsInstance.getLaunchTime

  def uptime = UptimeDisplay.print(launched)

  lazy val tags = awsInstance.getTags.map(t => t.getKey -> t.getValue).toMap.withDefaultValue("")

  lazy val stage = tags("Stage")
  lazy val app = tags("Role")
}

case class ElasticSearchInstance(publicDns: String) extends AppSpecifics {
  val baseUrl = s"http://$publicDns:9200"
  val versionUrl = baseUrl

  def usefulUrls = List(
    "head" -> (baseUrl + "/_plugin/head/"),
    "bigdesk" -> (baseUrl + "/_plugin/bigdesk/"),
    "paramedic" -> (baseUrl + "/_plugin/paramedic/")
  )

  val versionExtractor = { r: Response =>
    val v = (r.json \ "version" \ "number").as[String]
    val name = (r.json \ "name").as[String]
    Some(v + " " + name)
  }
}

case class StandardWebApp(publicDns: String, port: Int = 8080) extends AppSpecifics {
  lazy val versionUrl = s"http://$publicDns:$port/management/manifest"

  def usefulUrls = List(
    "manifest" -> versionUrl
  )

  val versionExtractor = { r: Response =>
    val values = r.body.lines.map(_.split(':').map(_.trim)).collect { case Array(k, v) => k -> v }.toMap
    values.get("Build")
  }
}

trait AppSpecifics {
  val log = Logger[AppSpecifics](classOf[AppSpecifics])

  def usefulUrls: Seq[(String, String)]
  def versionUrl: String
  def versionExtractor: Response => Option[String]
  def version = WS.url(versionUrl).withTimeout(2000).get() map (versionExtractor) recover {
    case e => {
      log.error(s"Couldn't retrieve $versionUrl", e)
      None
    }
  }
}

object Instance {
  import play.api.Play.current

  val log = Logger[Instance](classOf[Instance])

  private def uncachedGet(id: String)(implicit awsConn: AmazonConnection): Future[Instance] = {
    for {
      result <- AWS.futureOf(awsConn.ec2.describeInstancesAsync, new DescribeInstancesRequest().withInstanceIds(id))
      i <- (result.getReservations flatMap (_.getInstances) map (Instance(_))).head
    } yield i
  }

  def get(id: String)(implicit awsConn: AmazonConnection): Future[Instance] = {
    Cache.getAs[Instance](id) map (Future.successful(_)) getOrElse {
      uncachedGet(id) map { i =>
        Cache.set(id, i, 30)
        i
      }
    }
  }

  def apply(i: AwsEc2Instance): Future[Instance] = {
    val tags = i.getTags.map(t => t.getKey -> t.getValue).toMap.withDefaultValue("")
    val dns = i.getPublicDnsName

    val specifics =
      if (tags("Role").contains("elasticsearch")) new ElasticSearchInstance(dns)
      else new StandardWebApp(dns, Config.managementPort.getOrElse(9000))

    log.debug(s"Retrieveing version of instance with tags: $tags")
    specifics.version map { v =>
      Instance(i, v, specifics.usefulUrls)
    }
  }
}
