package model

import org.specs2.mutable._
import com.amazonaws.services.autoscaling.model.{TagDescription, AutoScalingGroup}
import collection.convert.wrapAll._

class EstateTest extends Specification {
  "Estate stages" should {
    "be sorted with PROD first" in {
      val estate = PopulatedEstate(Seq(
        ASG(new AutoScalingGroup().withTags(Seq(tag("Stage" -> "TEST"))), None, Seq(), Seq()),
        ASG(new AutoScalingGroup().withTags(Seq(tag("Stage" -> "PROD"))), None, Seq(), Seq()),
        ASG(new AutoScalingGroup().withTags(Seq(tag("Stage" -> "CODE"))), None, Seq(), Seq()),
        ASG(new AutoScalingGroup().withTags(Seq(tag("Stage" -> "QA"))), None, Seq(), Seq())
      ))
      estate.stages should contain(exactly("PROD", "CODE", "QA", "TEST"))
    }
  }

  def tag(keyValue: (String, String)) = {
    val (key, value) = keyValue
    new TagDescription().withKey(key).withValue(value)
  }
}
