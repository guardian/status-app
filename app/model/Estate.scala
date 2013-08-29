package model

import lib.{Config, AmazonConnection, AWS, ScheduledAgent}
import com.amazonaws.services.autoscaling.model.{TagDescription, AutoScalingGroup, DescribeAutoScalingGroupsRequest}
import scala.concurrent.Future
import controllers.Application
import com.amazonaws.services.sqs.model.ListQueuesRequest
import com.amazonaws.services.elasticloadbalancing.model.InstanceState
import scala.util.Random

trait Estate extends Map[String, Seq[ASG]] {
  def populated: Boolean
  def stageNames: Iterable[String]
  def queues: Seq[Queue]
  def -(key: String) = throw new UnsupportedOperationException
  def +[B1 >: Seq[ASG]](kv: (String, B1)) = throw new UnsupportedOperationException
  def asgs: Seq[ASG] = values.flatten.toSeq
}

case class PopulatedEstate(override val asgs: Seq[ASG], queues: Seq[Queue]) extends Estate {
  lazy val stageMap = asgs.groupBy(_.stage)
  def get(key: String) = stageMap.get(key)
  def iterator = stageMap.iterator

  lazy val stageNames = stageMap.keys.toSeq.sorted.sortWith((a, _) => if (a == "PROD") true else false)

  def populated = true
}

case object PendingEstate extends Estate {
  def get(key: String) = None
  def iterator = Nil.iterator
  def stageNames = Nil
  def populated = false
  def queues = Nil
}

object Estate {
  import scala.concurrent.duration._
  import scala.concurrent.ExecutionContext.Implicits.global
  import collection.convert.wrapAsScala._

  implicit val conn = AWS.connection

  val estateAgent = ScheduledAgent[Estate](0.seconds, 10.seconds, PendingEstate) {
    val groupsFuture = AWS.futureOf(conn.autoscaling.describeAutoScalingGroupsAsync, new DescribeAutoScalingGroupsRequest)
    val queuesFuture = AWS.futureOf(conn.sqs.listQueuesAsync, new ListQueuesRequest())

    for {
      groups <- groupsFuture
      asgs <- Future.traverse(groups.getAutoScalingGroups.toSeq)(ASG(_))
      queueResult <- queuesFuture
      queues <- Future.traverse(queueResult.getQueueUrls.toSeq)(Queue(_))
    } yield PopulatedEstate(asgs, queues)
  }
  def apply() =
//    PendingEstate
    EstateFixture()
    //estateAgent()
}

object EstateFixture {
  import collection.convert.wrapAll._
  def apply() = {
    val elb = ELB("ELB1", Nil, Nil, Nil)

    PopulatedEstate(Seq(
      WebAppASG(asg("Example 1", "PROD"), Some(elb), Seq(), Seq(member(), member(elbStatus = "OutOfService")), Nil),
      WebAppASG(asg("Example 2", "PROD"), None, Seq(), Seq(member(), member(autoScalingStatus = "Pending")), Nil),
      WebAppASG(asg("Example 3", "PROD"), None, Seq(), Seq.fill(10)(member()), Nil),
      WebAppASG(asg("Example 4", "PROD"), None, Seq(), Seq.fill(3)(member()), Nil),
      WebAppASG(asg("Example 6", "PROD"), None, Seq(), Seq.fill(2)(member()), Nil),
      WebAppASG(asg("Example 5", "PROD"), None, Seq(), Seq.fill(3)(member()), Nil),
      WebAppASG(asg("Example 7", "PROD"), None, Seq(), Seq(member()), Nil),
      WebAppASG(asg("Example 8", "PROD"), None, Seq(), Seq(member()), Nil),
      WebAppASG(asg("Example 9", "PROD"), None, Seq(), Seq(member()), Nil),
      WebAppASG(asg("Example 1", "CODE"), None, Seq(), Seq(member()), Nil)
    ), Nil)
  }

  def noStage() = {
    PopulatedEstate(Seq(
      ASG(new AutoScalingGroup(), None, Seq(), Seq(member(), member(elbStatus = "OutOfService")))
    ))
  }

  def asg(role: String, stage: String) =
    new AutoScalingGroup().withTags(Seq(
      new TagDescription().withKey("Role").withValue(role),
      new TagDescription().withKey("Stage").withValue(stage)))

  def member(id: String=s"i-${new String(Random.alphanumeric.take(6).toArray).toLowerCase}", autoScalingStatus : String="InService", elbStatus: String="InService") =
    ClusterMember(
      new com.amazonaws.services.autoscaling.model.Instance().withInstanceId(id).withLifecycleState(autoScalingStatus).withHealthStatus("Healthy"),
      Some(ELBMember(new InstanceState().withInstanceId(id).withState(elbStatus))),
      Instance(new com.amazonaws.services.ec2.model.Instance(), None, Seq()))
}

