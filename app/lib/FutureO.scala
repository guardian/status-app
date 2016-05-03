package lib

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class FutureO[T](futureOption: Future[Option[T]]) {

  def flatMap[S](f: T => FutureO[S]): FutureO[S] = {
    FutureO(futureOption.flatMap { optS =>
      optS.map(f(_).futureOption).getOrElse(Future.successful(None))
    })
  }

  def map[S](f: T => S): FutureO[S] = {
    FutureO(futureOption.map { optS =>
      optS.map(f(_))
    })
  }

}

object FutureO {

  def toOpt[T](f: Future[T]) = FutureO(f.map(Some(_)))

  def toFut[T](o: Option[T]) = FutureO(Future.successful(o))
}