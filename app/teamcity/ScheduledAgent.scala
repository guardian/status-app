package teamcity

import akka.actor.ActorSystem
import akka.agent.Agent
import play.api.libs.concurrent.Akka
import concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object ScheduledAgent {
  import play.api.Play.current

  implicit val system = Akka.system

  def apply[T](initialDelay: FiniteDuration, frequency: FiniteDuration)(block: => T): ScheduledAgent[T] = {
    new ScheduledAgent(initialDelay, frequency, block, _ => block, system)
  }

  def apply[T](initialDelay: FiniteDuration, frequency: FiniteDuration, initialValue: T)(block: T => T): ScheduledAgent[T] = {
    new ScheduledAgent(initialDelay, frequency, initialValue, block, system)
  }

}

class ScheduledAgent[T](initialDelay: FiniteDuration, frequency: FiniteDuration, initialValue: T, block: T => T, system: ActorSystem) {

  val agent = Agent[T](initialValue)(system)

  val agentSchedule = system.scheduler.schedule(initialDelay, frequency) {
    agent sendOff (block)
  }

  def get(): T = agent()

  def apply(): T = get()

  def shutdown() {
    agentSchedule.cancel()
  }

}
