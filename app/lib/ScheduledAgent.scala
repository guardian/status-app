package lib

import akka.actor.ActorSystem
import akka.agent.Agent
import play.api.libs.concurrent.Akka
import concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ScheduledAgent {
  import play.api.Play.current

  implicit val system = Akka.system

  def apply[T](initialDelay: FiniteDuration, frequency: FiniteDuration, initialValue: T)(block: => Future[T]): ScheduledAgent[T] = {
    new ScheduledAgent(initialDelay, frequency, initialValue, block, system)
  }
}

class ScheduledAgent[T](initialDelay: FiniteDuration, frequency: FiniteDuration, initialValue: T, block: => Future[T], system: ActorSystem) {

  val agent = Agent[T](initialValue)(system)

  val agentSchedule = system.scheduler.schedule(initialDelay, frequency) {
    for {
      result <- block
    } agent send result
  }

  def get(): T = agent()

  def apply(): T = get()

  def shutdown() {
    agentSchedule.cancel()
  }

}
