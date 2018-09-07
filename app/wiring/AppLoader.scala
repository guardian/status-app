package wiring

import akka.actor.ActorSystem
import controllers.{Login, Management, StatusAppAuthAction, routes, Application => ApplicationController}
import lib.DynamoConfig
import model._
import play.api.ApplicationLoader.Context
import play.api.cache.ehcache.EhCacheComponents
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{AnyContent, EssentialFilter}
import play.api.{Application, ApplicationLoader, BuiltInComponentsFromContext, LoggerConfigurator}
import router.Routes


class AppLoader extends ApplicationLoader {
  override def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment)
    }
    new MyComponents(context).application
  }
}

class MyComponents(context: Context)
  extends BuiltInComponentsFromContext(context)
    with AhcWSComponents
    with EhCacheComponents
    with controllers.AssetsComponents {
  override def httpFilters: Seq[EssentialFilter] = Seq.empty

  implicit val system: ActorSystem = actorSystem
  val dynamoConfig = new DynamoConfig(environment.mode, httpConfiguration)

  implicit val ws = wsClient
  val awsCost = new AWSCost
  val asgSource = new ASGSource(awsCost)
  val getEstate = new EstateProvider(asgSource)
  val googleAuthConfig = dynamoConfig.googleAuthConfig
  val authAction = new StatusAppAuthAction[AnyContent](
    googleAuthConfig,
    routes.Login.login(),
    controllerComponents.parsers.default
  )(executionContext)

  lazy val router: Routes = new Routes(
    httpErrorHandler,
    new ApplicationController(wsClient, authAction, awsCost, getEstate, controllerComponents),
    new Login(googleAuthConfig, wsClient, controllerComponents, authAction),
    assets,
    new Management(getEstate)
  )
}