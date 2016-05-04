package model

import org.specs2.mutable._
import com.amazonaws.services.autoscaling.model.{TagDescription, AutoScalingGroup}
import collection.convert.wrapAll._
import org.joda.time.DateTime
import scala.util.Random

class EstateTest extends Specification {
  "Estate stages" should {
    "be sorted with PROD first" in {
      val estate = PopulatedEstate(Seq(
        asg("TEST"),
        asg("PROD"),
        asg("CODE"),
        asg("QA")
      ), Seq(), DateTime.now)
      estate.stageNames should contain(exactly("PROD", "CODE", "QA", "TEST"))
    }
  }

  "Should have a list of stages, each with all relevant stacks" in {
    val estate = PopulatedEstate(Seq(
      asg("PROD", Some("a")),
      asg("PROD", Some("a")),
      asg("PROD", Some("b")),
      asg("PROD", None)
    ), Seq(), DateTime.now)
    estate("PROD").stacks should haveLength(3)
  }

  "Stacks should be ordered by number of instances" in {
    val estate = PopulatedEstate(Seq(
      asg("PROD", Some("b"), 1),
      asg("PROD", Some("b"), 1),
      asg("PROD", Some("a"), 3),
      asg("PROD", None)
    ), Seq(), DateTime.now)
    estate("PROD").stacks.map(_.name).toSeq should be_== (Seq("a", "b", "unknown"))
  }

  def asg(stage: String, stack: Option[String] = None, numMembers: Int = 1) = ASG(
    Some(s"name-${Random.alphanumeric}"), Some(stage), None, stack, None,
      Seq.fill(numMembers)(ASGMember(Random.alphanumeric.toString(), None, "", None, None, None, "", null)), Nil, Nil, Nil, None, None
  )
}
