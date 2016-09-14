package model

import com.amazonaws.services.autoscaling.model.{Instance => AwsAsgInstance, _}
import play.api.libs.json.Json

case class ASGMember(id: String, description: Option[String], uptime: String, version: Option[String],
                     state: Option[String], lifecycleState: Option[String], goodorbad: String, instance: Instance)

object ASGMember {
  implicit val memberWrites = Json.writes[ASGMember]

  def from(instance: Instance, asgInfo: Option[AwsAsgInstance], elbInfo: Option[ELBMember]): ASGMember = {
    def healthStatus = asgInfo.map(_.getHealthStatus)
    def lifecycleState = asgInfo.map(_.getLifecycleState)

    def lbState = elbInfo.map(_.state)
    def description = elbInfo.flatMap(_.description)

    def instanteState = instance.state

    def goodorbad = (healthStatus, lifecycleState, lbState, instanteState) match {
      case (_, Some("Pending"), _, _) | (_, Some("Terminating"), _, _) => "pending"
      case (Some("Healthy"), Some("InService"), Some("InService"), _) => "success"
      case (Some("Healthy"), Some("InService"), None, _) => "success"
      case (None, None, None, "running") => "success"
      case _ => "danger"
    }

    val truncatedId = {
      if(instance.id.length > 10) {
        s"${instance.id.take(8)}..."
      } else instance.id
    }

    ASGMember(truncatedId, description, instance.uptime, instance.version, lbState, lifecycleState, goodorbad, instance)
  }

}




