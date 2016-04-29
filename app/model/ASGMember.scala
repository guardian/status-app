package model

import com.amazonaws.services.autoscaling.model.{Instance => AwsAsgInstance, _}
import play.api.libs.json.Json

case class ASGMember(id: String, description: Option[String], uptime: String, version: Option[String],
                     state: Option[String], lifecycleState: String, goodorbad: String, instance: Instance)

object ASGMember {
  implicit val memberWrites = Json.writes[ASGMember]

  def from(asgInfo: AwsAsgInstance, elbInfo: Option[ELBMember], instance: Instance): ASGMember = {
    def healthStatus = asgInfo.getHealthStatus
    def lifecycleState = asgInfo.getLifecycleState

    def state = elbInfo.map(_.state)
    def description = elbInfo.flatMap(_.description)

    def goodorbad = (healthStatus, lifecycleState, state) match {
      case (_, "Pending", _) | (_, "Terminating", _) => "pending"
      case ("Healthy", "InService", Some("InService")) => "success"
      case ("Healthy", "InService", None) => "success"
      case _ => "danger"
    }
    ASGMember(asgInfo.getInstanceId, description, instance.uptime, instance.version,
      state, lifecycleState, goodorbad, instance)
  }

  def from(instance: Instance): ASGMember = {
    ASGMember(instance.id, None, instance.uptime, instance.version, None, "lifecycle", "healthStatus", instance)
  }

}




