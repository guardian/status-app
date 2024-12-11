package model

import java.net.ConnectException
import java.util.Date

import com.amazonaws.services.ec2.model.{DescribeInstancesRequest, Instance => AwsEc2Instance}
import lib._
import play.api.Logger
import play.api.cache.SyncCacheApi
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

case class Instance(
  id: String,
  publicDns: String,
  publicIp: String,
  privateDns: String,
  privateIp: String,
  instanceType: String,
  state: String,

  availabilityZone: String,

  cost: Option[BigDecimal],
  approxMonthlyCost: Option[BigDecimal],
  costingType: EC2CostingType,

  uptime: String,
  launched: Date,

  tags: Map[String, String],
  stage: String,
  app: String,

  version: Option[String],

  usefulUrls: Map[String, String]
) {
  val nameOpt = tags.get("Name")
}


object EC2Instance {
  def apply(awsInstance: AwsEc2Instance, version: Option[String], usefulUrls: Seq[(String, String)], awsCost: AWSCost, flag:Boolean = false) = {
    val tags = awsInstance.getTags.asScala.map(t => t.getKey -> t.getValue).toMap.withDefaultValue("")
    val costingType = EC2CostingType(awsInstance.getInstanceType, awsInstance.getPlacement.getAvailabilityZone)
    val cost: Option[BigDecimal] = Try(awsCost(costingType)).toOption
    Instance(
      id = awsInstance.getInstanceId,
      publicDns = awsInstance.getPublicDnsName,
      publicIp = awsInstance.getPublicIpAddress,
      privateDns = awsInstance.getPrivateDnsName,
      privateIp = awsInstance.getPrivateIpAddress,
      instanceType = awsInstance.getInstanceType,
      state = awsInstance.getState.getName,

      availabilityZone = awsInstance.getPlacement.getAvailabilityZone,

      cost = cost,
      approxMonthlyCost = cost map (_ * 24 * 30),
      costingType = costingType,

      launched = awsInstance.getLaunchTime,

      uptime = UptimeDisplay.print(awsInstance.getLaunchTime),

      tags = tags,

      stage = tags("Stage"),
      app = tags("App"),

      version = version,
      usefulUrls = usefulUrls.toMap
    )
  }
}

case class ElasticSearchInstance(privateDns: String) extends AppSpecifics {
  val baseUrl = s"http://$privateDns:9200"
  val versionUrl = baseUrl

  def usefulUrls = List(
    "head" -> (baseUrl + "/_plugin/head/"),
    "bigdesk" -> (baseUrl + "/_plugin/bigdesk/"),
    "paramedic" -> (baseUrl + "/_plugin/paramedic/")
  )

  val versionExtractor = { r: WSResponse =>
    val v = (r.json \ "version" \ "number").as[String]
    val name = (r.json \ "name").as[String]
    Some(v + " " + name)
  }
}

case class StandardWebApp(versionUrl: String) extends AppSpecifics {

  def usefulUrls = List(
    "manifest" -> versionUrl
  )

  val versionExtractor = { r: WSResponse =>
    val values = r.body.linesIterator.map(_.split(':').map(_.trim)).collect { case Array(k, v) => k -> v }.toMap
    values.get("Build")
  }
}

trait AppSpecifics {
  val log = Logger(classOf[AppSpecifics])

  def usefulUrls: Seq[(String, String)]

  def versionUrl: String

  def versionExtractor: WSResponse => Option[String]

  def version(implicit wsClient: WSClient) :Future[Option[String]] = {

    (for {
      wsWithUrl <- Future.fromTry(Try(wsClient.url(versionUrl)))
      response <- wsWithUrl.withRequestTimeout(200.milliseconds).get() map (versionExtractor)
    } yield response) recover {
      case _: ConnectException =>
        log.error(s"Couldn't retrieve $versionUrl")
        None
      case e =>
        log.error(s"Couldn't retrieve $versionUrl", e)
        None
    }
  }
}


object Instance {
  implicit val instanceWrites = Json.writes[Instance]
  val log = Logger(classOf[Instance])

  private def uncachedGet(id: String, awsCost: AWSCost)(implicit awsConn: AmazonConnection, wsClient: WSClient): Future[Instance] = {
    (for {
      result <- AWS.futureOf(awsConn.ec2.describeInstancesAsync, new DescribeInstancesRequest().withInstanceIds(id))
      i <- (result.getReservations.asScala flatMap (_.getInstances.asScala) map (from(_, awsCost))).head
    } yield i) recover {
      case e => {
        log.error(s"Unable to retrieve details for instance: $id")
        UnknownInstance(id)
      }
    }
  }

  def get(id: String, awsCost: AWSCost)(implicit awsConn: AmazonConnection, cache: SyncCacheApi, wsClient: WSClient ): Future[Instance] =
    cache.get[Instance](id) map (Future.successful(_)) getOrElse {
      uncachedGet(id, awsCost) map { instance: Instance =>
        cache.set(id, instance, Duration(30, SECONDS))
        instance
      }
    }

  def from(i: AwsEc2Instance, awsCost: AWSCost)(implicit wsClient: WSClient): Future[Instance] = {
    val tags = i.getTags.asScala.map(t => t.getKey -> t.getValue).toMap.withDefaultValue("")
    val dns = i.getPublicDnsName

    val managementTag = ManagementTag(tags.get("Management"))
    val managementEndpoint = managementTag map (ManagementEndpoint(dns, _))

    val specifics =
      if (managementTag.flatMap(_.format).exists(_ == "elasticsearch"))
        new ElasticSearchInstance(i.getPrivateDnsName)
      else
        new StandardWebApp(s"${managementEndpoint.get.url}/manifest")

    log.debug(s"Retrieving version of instance with tags: $tags")
    specifics.version map {
      v => EC2Instance(i, v, specifics.usefulUrls, awsCost)
    }
  }
}

object ManagementEndpoint {
  def apply(dnsName: String, tag: ManagementTag): ManagementEndpoint = {
    val port: Int = tag.port.orElse(Config.managementPort).getOrElse(9000)
    val protocol: String = tag.protocol getOrElse "http"
    val path: String = tag.path getOrElse "/management"

    def url: String = s"""$protocol://$dnsName:$port$path"""

    ManagementEndpoint(dnsName, tag, port, protocol, path, url)
  }
}

case class ManagementEndpoint(dnsName: String, tag: ManagementTag, port: Int, protocol: String, path: String, url: String)

object UnknownInstance {
  def apply(id: String) = Instance(
    id = id,
    app = "???",
    stage = "???",
    uptime = "???",
    state = "???",
    cost = None,
    approxMonthlyCost = None,
    availabilityZone = "???",
    instanceType = "???",
    privateIp = "???",
    privateDns = "???",
    publicIp = "???",
    publicDns = "???",
    costingType = EC2CostingType("", ""),
    version = None,
    launched = new Date(),
    usefulUrls = Map.empty,
    tags = Map.empty
  )
}
