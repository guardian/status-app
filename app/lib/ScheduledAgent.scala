package lib

import akka.actor.ActorSystem
import akka.agent.Agent
import concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import play.Logger

class GetScheduledAgent(implicit system: ActorSystem) {

  def apply[T](initialDelay: FiniteDuration, frequency: FiniteDuration, initialValue: T)(block: => Future[T]): ScheduledAgent[T] = {
    new ScheduledAgent(initialDelay, frequency, initialValue, block, system)
  }
}

class ScheduledAgent[T](initialDelay: FiniteDuration, frequency: FiniteDuration, initialValue: T, block: => Future[T], system: ActorSystem)(implicit ec: ExecutionContext) {

  val agent = Agent[T](initialValue)

  val agentSchedule = system.scheduler.schedule(initialDelay, frequency) {
    block.onComplete {
      case Failure(e) => Logger.warn("scheduled agent failed", e)
      case Success(result) => agent send result
    }
  }

  def get(): T = agent()

  def apply(): T = get()

  def shutdown() {
    agentSchedule.cancel()
  }

}
