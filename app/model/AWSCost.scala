package model

import play.api.libs.ws.WS
import play.api.libs.json._
import controllers.Application
import collection.JavaConversions._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try
import play.api.libs.json.JsArray
import play.api.libs.json.JsSuccess
import play.api.Logger
import com.amazonaws.services.ec2.model.{DescribeReservedInstancesRequest, DescribeInstancesRequest}
import lib.{AWS, ScheduledAgent}


object AWSCost {
  val logger = Logger(getClass)

  implicit val awsConnection = Application.amazonConnection

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
      .instanceTypes(typeToCostSize(costType.instanceType))
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
      instances <- Future.sequence (
        reservations.getReservations flatMap (_.getInstances) map (Instance(_))
      )
    } yield instances.groupBy(_.costingType).mapValues(_.size)
  }

  lazy val reservationsAgent = ScheduledAgent[Map[EC2CostingType, Seq[Reservation]]](0.seconds, 5.minutes, Map()) {
    logger.info("Starting reservationsAgent")
    for {
      reservations <- AWS.futureOf(awsConnection.ec2.describeReservedInstancesAsync, new DescribeReservedInstancesRequest())
    } yield {
      val reservs = reservations.getReservedInstances map { r =>
        (EC2CostingType(r.getInstanceType ,r.getAvailabilityZone) ->
          Reservation(r.getInstanceCount, r.getFixedPrice, r.getRecurringCharges.head.getAmount))
      }
      reservs.foreach{ res => logger.info("Reservation: "+res)}
      reservs.groupBy { case (costType, _) => costType } mapValues (_ map { case (_, res) => res })
    }
  }

  lazy val costsAgent = ScheduledAgent[OnDemandPrices](0.seconds, 30.minutes, OnDemandPrices(Map())) {
    // There isn't a proper API for this at time of writing, but handily the
    logger.info("Starting costsAgent")
    val f = (WS.url("http://aws.amazon.com/ec2/pricing/pricing-on-demand-instances.json").withTimeout(2000).get map { response =>
      logger.info("Fetched cost data")
      implicit object BigDecimalReads extends Reads[BigDecimal]{
        def reads(json: JsValue) = JsSuccess(Try { BigDecimal(json.as[String]) } getOrElse (BigDecimal(0)) )
      }
      implicit object RegionPricesReads extends Reads[RegionPrices] {
        def reads(json: JsValue) = {
          val JsArray(typeGroups) = json
          val typeToCost = for {
            group <- typeGroups
            typePrefix = (group \ "type").as[String]
            JsArray(size) = (group \ "sizes")
            s <- size
          } yield {
            val JsArray(c) = (s \ "valueColumns")
            (typePrefix + "_" + (s \ "size").as[String] -> (c.head \ "prices" \ "USD").as[BigDecimal])
          }

          JsSuccess(RegionPrices(Map(typeToCost: _*)))
        }
      }
      implicit object OnDemandPricesReads extends Reads[OnDemandPrices] {
        def reads(json: JsValue) = {
          val JsArray(regionsJs) = (json \ "config" \ "regions")
          val regions = regionsJs.map { r =>
            ((r \ "region").as[String] -> (r \ "instanceTypes").as[RegionPrices])
          }
          JsSuccess(OnDemandPrices(Map(regions: _*)))
        }
      }
      val prices = response.json.as[OnDemandPrices]
      logger.info("Prices are: "+prices)
      prices
    })

    f
  }

  val zoneToCostRegion = Map(
    "eu-west-1a" -> "eu-ireland",
    "eu-west-1b" -> "eu-ireland",
    "eu-west-1c" -> "eu-ireland",
    "us-east-1a" -> "us-east",
    "us-east-1b" -> "us-east",
    "us-east-1c" -> "us-east",
    "us-east-1d" -> "us-east",
    "us-west-1a" -> "us-west",
    "us-west-1b" -> "us-west",
    "us-west-1c" -> "us-west",
    "us-west-2a" -> "us-west-2",
    "us-west-2b" -> "us-west-2",
    "ap-northeast-1a" -> "apac-tokyo",
    "ap-northeast-1b" -> "apac-tokyo",
    "ap-southeast-1a" -> "apac-sin",
    "ap-southeast-1b" -> "apac-sin",
    "ap-southeast-2a" -> "apac-syd",
    "ap-southeast-2b" -> "apac-syd",
    "sa-east-1a" -> "sa-east-1",
    "sa-east-1b" -> "sa-east-1"
  )

  val typeToCostSize = Map(
    "m1.small" -> "stdODI_sm",
    "m1.medium" -> "stdODI_med",
    "m1.large" -> "stdODI_lg",
    "m1.xlarge" -> "stdODI_xl",
    "m3.xlarge" -> "secgenstdODI_xl",
    "m3.2xlarge" -> "secgenstdODI_xxl",
    "t1.micro" -> "uODI_u",
    "m2.xlarge" -> "hiMemODI_xl",
    "m2.2xlarge" -> "hiMemODI_xxl",
    "m2.4xlarge" -> "hiMemODI_xxxxl",
    "c1.medium" -> "hiCPUODI_med",
    "c1.xlarge" -> "hiCPUODI_xl",
    "cc2.4xlarge" -> "clusterComputeI_xxxxl",
    "cc2.8xlarge" -> "clusterComputeI_xxxxxxxxl",
    "cr1.8xlarge" -> "clusterHiMemODI_xxxxxxxxl",
    "cg1.4xlarge" -> "clusterGPUI_xxxxl",
    "hi1.4xlarge" -> "hiIoODI_xxxxl",
    "hs1.8xlarge" -> "hiStoreODI_xxxxxxxxl"
  )
}

case class OnDemandPrices(regions: Map[String, RegionPrices])
case class RegionPrices(instanceTypes: Map[String, BigDecimal])

case class EC2CostingType(instanceType: String, zone: String)
case class Reservation(count: Int, fixedPrice: Float, hourlyRate: Double) {
  def hourlyCost = count * hourlyRate
  def sunkCost = count * fixedPrice
}



