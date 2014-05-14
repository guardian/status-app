package model

import org.specs2.mutable._
import com.amazonaws.services.autoscaling.model.{TagDescription, AutoScalingGroup}
import collection.convert.wrapAll._
import org.joda.time.DateTime

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

  def tag(keyValue: (String, String)) = {
    val (key, value) = keyValue
    new TagDescription().withKey(key).withValue(value)
  }

  def asg(stage: String) = ASG(
    "name", Some(stage), None, None, None, Nil, Nil, Nil, Nil, None
  )
}
