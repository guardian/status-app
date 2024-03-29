package controllers


import cats.data.EitherT
import com.gu.googleauth.{AuthAction, GoogleAuthConfig, GoogleAuthFilters, UserIdentity}
import play.api.mvc.Results.{Redirect, Unauthorized}
import play.api.mvc.{BodyParser, Call, RequestHeader}
import com.gu.googleauth._
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class StatusAppAuthAction[A](authConfig: GoogleAuthConfig,
  loginTarget: Call,
  bodyParser: BodyParser[A])
  (implicit ex: ExecutionContext) extends AuthAction[A](authConfig, loginTarget, bodyParser)(ex) {
  override def sendForAuth[A](request: RequestHeader)(implicit ec: ExecutionContext) = {
    if (request.accepts("text/html")) {
      Redirect(loginTarget).withSession {
        request.session + (GoogleAuthFilters.LOGIN_ORIGIN_KEY -> request.uri)
      }
    } else {
      Unauthorized
    }
  }

}

object AuthorisationValidator {
  def isAuthorised(id: UserIdentity) = authorisationError(id).isEmpty

  def authorisationError(id: UserIdentity): Option[String] = if (id.emailDomain != "guardian.co.uk") Some(s"The email you are using to login: ${id.email}. Please try again with another email") else None
}

class Login(
  googleAuthConfig: GoogleAuthConfig,
  override val wsClient: WSClient,
  controllerComponents: ControllerComponents)
  (implicit val executionContext: ExecutionContext) extends AbstractController(controllerComponents) with LoginSupport {

  override val authConfig = googleAuthConfig
  override val failureRedirectTarget: Call = routes.Application.index
  override val defaultRedirectTarget: Call = routes.Application.index


  def login = Action { request =>
    val error = request.flash.get("error")
    Ok(views.html.login(error))
  }

  def logout = Action { implicit request =>
    Redirect(routes.Application.index).flashing("error" -> s"User logged out").withNewSession
  }

  def loginAction = Action.async { implicit request =>
    startGoogleLogin()
  }

  def oauth2Callback = Action.async { implicit request =>
    import cats.instances.future._
    (for {
      identity <- checkIdentity()
      _ <- EitherT.fromEither[Future] {
        if (AuthorisationValidator.isAuthorised(identity))
          Right(())
        else Left(redirectWithError(failureRedirectTarget, AuthorisationValidator.authorisationError(identity).getOrElse("Bad, unknown error")))
      }
    } yield {
      setupSessionWhenSuccessful(identity)
    }).merge
  }
}