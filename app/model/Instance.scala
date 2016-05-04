package model

import com.amazonaws.services.ec2.model.{Instance => AwsEc2Instance, DescribeInstancesRequest}
import lib._
import collection.JavaConversions._
import play.api.libs.ws.{WSResponse, WS}
import play.api.cache.Cache
import scala.concurrent.ExecutionContext.Implicits.global
import concurrent.Future
import scala.Some
import play.api.Logger
import scala.util.Try
import java.util.Date
import java.net.ConnectException
import play.api.libs.json.Json

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
)

object EC2Instance {
  def apply(awsInstance: AwsEc2Instance, version: Option[String], usefulUrls: Seq[(String, String)]) = {
    val tags = awsInstance.getTags.map(t => t.getKey -> t.getValue).toMap.withDefaultValue("")
    val costingType = EC2CostingType(awsInstance.getInstanceType, awsInstance.getPlacement.getAvailabilityZone)
    val cost = Try(AWSCost(costingType)).toOption
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

case class ElasticSearchInstance(publicDns: String) extends AppSpecifics {
  val baseUrl = s"http://$publicDns:9200"
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
    val values = r.body.lines.map(_.split(':').map(_.trim)).collect { case Array(k, v) => k -> v }.toMap
    values.get("Build")
  }
}

trait AppSpecifics {
  import play.api.Play.current

  val log = Logger[AppSpecifics](classOf[AppSpecifics])

  def usefulUrls: Seq[(String, String)]
  def versionUrl: String
  def versionExtractor: WSResponse => Option[String]
  def version = WS.url(versionUrl).withRequestTimeout(200).get() map (versionExtractor) recover {
    case _: ConnectException => {
      log.error(s"Couldn't retrieve $versionUrl")
      None
    }
    case e => {
      log.error(s"Couldn't retrieve $versionUrl", e)
      None
    }
  }
}

object Instance {
  import play.api.Play.current

  implicit val instanceWrites = Json.writes[Instance]

  val log = Logger[Instance](classOf[Instance])

  private def uncachedGet(id: String)(implicit awsConn: AmazonConnection): Future[Instance] = {
    (for {
      result <- AWS.futureOf(awsConn.ec2.describeInstancesAsync, new DescribeInstancesRequest().withInstanceIds(id))
      i <- (result.getReservations flatMap (_.getInstances) map (Instance.from(_))).head
    } yield i) recover {
      case e => {
        log.error(s"Unable to retrieve details for instance: $id")
        UnknownInstance(id)
      }
    }
  }

  def get(id: String)(implicit awsConn: AmazonConnection): Future[Instance] = {
    Cache.getAs[Instance](id) map (Future.successful(_)) getOrElse {
      uncachedGet(id) map { i =>
        Cache.set(id, i, 30)
        i
      }
    }
  }

  def from(i: AwsEc2Instance): Future[Instance] = {
    val tags = i.getTags.map(t => t.getKey -> t.getValue).toMap.withDefaultValue("")
    val dns = i.getPublicDnsName

    val managementTag = ManagementTag(tags.get("Management"))
    val managementEndpoint = managementTag map (ManagementEndpoint(dns, _))

    val specifics =
      if (managementTag.flatMap(_.format).exists(_ == "elasticsearch")) new ElasticSearchInstance(dns)
      else new StandardWebApp(s"${managementEndpoint.get.url}/manifest")

    log.debug(s"Retrieving version of instance with tags: $tags")
    specifics.version map { v =>
      EC2Instance(i, v, specifics.usefulUrls)
    }
  }
}

case class ManagementEndpoint(dnsName:String, tag: ManagementTag) {
  val port = tag.port.orElse(Config.managementPort).getOrElse(9000)
  val protocol = tag.protocol getOrElse "http"
  val path = tag.path getOrElse "/management"
  def url: String = s"""$protocol://$dnsName:$port$path"""
}

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
