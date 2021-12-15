package lib

import akka.actor.ActorSystem
import play.Logger

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object ScheduledAgent {

  def apply[T](initialDelay: FiniteDuration, frequency: FiniteDuration, initialValue: T)(block: => Future[T])
    (implicit system: ActorSystem, ec: ExecutionContext): ScheduledAgent[T] = {
    new ScheduledAgent(initialDelay, frequency, initialValue, block, system)
  }
}

class ScheduledAgent[T](initialDelay: FiniteDuration, frequency: FiniteDuration, initialValue: T, block: => Future[T], system: ActorSystem)(implicit ec: ExecutionContext) {

  val agent = new AtomicReference[T](initialValue)

  val agentSchedule = system.scheduler.schedule(initialDelay, frequency) {
    block.onComplete {
      case Failure(e) => Logger.warn("scheduled agent failed", e)
      case Success(result) => agent.set(result)
    }
  }

  def get(): T = agent.get()

  def apply(): T = get()

  def shutdown() {
    agentSchedule.cancel()
  }

}
