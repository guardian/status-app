package model

import akka.actor.ActorSystem
import play.api.libs.json._

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try
import play.api.libs.json.JsArray
import play.api.libs.json.JsSuccess
import play.api.Logger
import com.amazonaws.services.ec2.model.{DescribeInstancesRequest, DescribeReservedInstancesRequest, Filter}
import lib.{AWS, ScheduledAgent}
import play.api.libs.ws.WSClient

import scala.concurrent.duration._

class AWSCost(implicit wsClient: WSClient, system: ActorSystem) {
  val logger = Logger(getClass)

  implicit val awsConnection = AWS.connection

  def apply(costType: EC2CostingType) = {
    val onDemandRate: BigDecimal = onDemandPriceFor(costType)
    val totalInstances: Int = countForType(costType)
    val reservations = reservationsFor(costType)
    val reservationCount: Int = reservations.map(_.count).sum
    val reservationRate: Double = reservationCount match {
      case 0 => 0
      case resCount => (reservations.map(_.hourlyCost).sum) / resCount
    }
    ((totalInstances - reservationCount) * onDemandRate + reservationCount * reservationRate) / totalInstances
  }

  def countForType(costType: EC2CostingType) = typeCounts().getOrElse(costType, 0)

  def onDemandPriceFor(costType: EC2CostingType) = {
    costsAgent()
      .regions(zoneToCostRegion(costType.zone))
      .instanceTypes(costType.instanceType)
  }

  def totalSunkCost = (for {
    resList <- reservations.values
    res <- resList
  } yield res.sunkCost).sum

  def reservationsFor(costType: EC2CostingType) = reservations.getOrElse(costType, Seq())

  def reservations = reservationsAgent()

  val typeCounts = ScheduledAgent[Map[EC2CostingType, Int]](0.seconds, 5.minutes, Map()) {
    for {
      reservations <- AWS.futureOf(awsConnection.ec2.describeInstancesAsync, new DescribeInstancesRequest())
      instances =  reservations.getReservations.asScala flatMap (_.getInstances.asScala)
    } yield instances.groupBy(i => EC2CostingType(i.getInstanceType, i.getPlacement.getAvailabilityZone)).view.mapValues(_.size).toMap
  }

  lazy val reservationsAgent = ScheduledAgent[Map[EC2CostingType, Seq[Reservation]]](0.seconds, 5.minutes, Map()) {
    logger.info("Starting reservationsAgent")
    for {
      reservations <- AWS.futureOf(awsConnection.ec2.describeReservedInstancesAsync,
        new DescribeReservedInstancesRequest().withFilters(new Filter("state", List("active").asJava)))
    } yield {
      val reservs = reservations.getReservedInstances.asScala.toSeq map { r =>
        EC2CostingType(r.getInstanceType, r.getAvailabilityZone) ->
          Reservation(r.getInstanceCount, r.getFixedPrice, r.getRecurringCharges.asScala.headOption.map(_.getAmount.toDouble).getOrElse(0d))
      }
      reservs.foreach{ res => logger.info("Reservation: "+res)}
      reservs.groupBy { case (costType, _) => costType }.view.mapValues(_ map { case (_, res) => res }).toMap
    }
  }

  lazy val costsAgent = ScheduledAgent[OnDemandPrices](0.seconds, 30.minutes, OnDemandPrices(Map())) {
    // There isn't a proper API for this at time of writing, but handily the
    logger.info("Starting costsAgent")
    def pricesFromJson(url: String) = wsClient.url(url).withRequestTimeout(2.seconds).get map { response =>
      logger.info("Fetched cost data")
      implicit object BigDecimalReads extends Reads[BigDecimal]{
        def reads(json: JsValue) = JsSuccess(Try { BigDecimal(json.as[String]) } getOrElse (BigDecimal(0)) )
      }
      implicit object RegionPricesReads extends Reads[RegionPrices] {
        def reads(json: JsValue) = {
          val JsArray(typeGroups) = json
          val typeToCost = for {
            group <- typeGroups
            JsArray(size) = (group \ "sizes").get
            s <- size
          } yield {
            val JsArray(c) = (s \ "valueColumns").get
            (s \ "size").as[String] -> (c.head \ "prices" \ "USD").as[BigDecimal]
          }

          JsSuccess(RegionPrices(typeToCost.toMap))
        }
      }
      implicit object OnDemandPricesReads extends Reads[OnDemandPrices] {
        def reads(json: JsValue) = {
          val JsArray(regionsJs) = (json \ "config" \ "regions").get
          val regions = regionsJs.map { r =>
            (r \ "region").as[String] -> (r \ "instanceTypes").as[RegionPrices]
          }
          JsSuccess(OnDemandPrices(regions.toMap))
        }
      }
      Json.parse(response.body.dropWhile(_ != '{').takeWhile(_ != ')')).as[OnDemandPrices]
    }

    for {
      current <- pricesFromJson("http://aws.amazon.com/ec2/pricing/json/linux-od.json")
      old <- pricesFromJson("https://a0.awsstatic.com/pricing/1/deprecated/ec2/previous-generation/linux-od.json")
    } yield current ++ old
  }

  val zoneToCostRegion: String => String = _.dropRight(1)
}

case class OnDemandPrices(regions: Map[String, RegionPrices]) {
  def ++ (other: OnDemandPrices) = OnDemandPrices(regions.foldLeft(other.regions){
    case (m, (regionName, regionPrices)) =>
      (
        for {
          myPrices <- m.get(regionName)
        } yield m.updated(regionName, RegionPrices(regionPrices.instanceTypes ++ myPrices.instanceTypes))
      ).getOrElse(m + (regionName -> regionPrices))
  })
}
case class RegionPrices(instanceTypes: Map[String, BigDecimal])

case class EC2CostingType(instanceType: String, zone: String)
object EC2CostingType {
  implicit val writes = Json.writes[EC2CostingType]
}
case class Reservation(count: Int, fixedPrice: Float, hourlyRate: Double) {
  def hourlyCost = count * hourlyRate
  def sunkCost = count * fixedPrice
}



