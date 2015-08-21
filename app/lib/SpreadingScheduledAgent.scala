package lib

import akka.actor.ActorSystem
import akka.agent.Agent
import play.api.Logger
import play.api.libs.concurrent.Akka

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Success, Failure}

object SpreadingScheduledAgent {
  import play.api.Play.current

  implicit val system = Akka.system

  def apply[T](initialDelay: FiniteDuration, frequency: FiniteDuration, initialValue: T)(block: => Future[List[() => Future[T => T]]]): SpreadingScheduledAgent[T] = {
    new SpreadingScheduledAgent(initialDelay, frequency, initialValue, block, system)
  }
}

class SpreadingScheduledAgent[T](initialDelay: FiniteDuration, frequency: FiniteDuration, initialValue: T,
  block: => Future[List[() => Future[T => T]]], system: ActorSystem
)(implicit ec: ExecutionContext) {

  val agent = Agent[T](initialValue)

  val log = Logger[SpreadingScheduledAgent[T]](classOf[SpreadingScheduledAgent[T]])

  private def delayedUpdate(delay: FiniteDuration, update: () => Future[T => T]) = {
    system.scheduler.scheduleOnce(delay) {
      update().onComplete {
        case Success(result) => agent send result
        case Failure(e) => Logger.warn("scheduling agent part failure", e)
      }
    }
  }

  val agentSchedule = system.scheduler.schedule(initialDelay, frequency) {
    block.onComplete {
      case Success(result) =>
        result.zipWithIndex foreach { case (update, index) =>
          delayedUpdate(index * frequency / result.length, update)
        }
      case Failure(e) => Logger.warn("scheduled agent failed", e)
    }
  }

  def get(): T = agent()

  def apply(): T = get()

  def shutdown() {
    agentSchedule.cancel()
  }

}