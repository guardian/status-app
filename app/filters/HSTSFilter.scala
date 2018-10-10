package filters

import play.api.http.HeaderNames.STRICT_TRANSPORT_SECURITY
import play.api.mvc.{EssentialAction, EssentialFilter}

import scala.concurrent.ExecutionContext

class HSTSFilter(implicit ec: ExecutionContext) extends EssentialFilter {
  def apply(next: EssentialAction) = EssentialAction { req =>
    next(req).map(result => result.withHeaders(STRICT_TRANSPORT_SECURITY -> "max-age=31536000"))
  }
}
