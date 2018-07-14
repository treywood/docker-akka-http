package net.treywood.http.apis

import java.util.concurrent.TimeUnit

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteConcatenation.RouteWithConcatenation
import akka.http.scaladsl.server.{PathMatcher, Route}
import akka.util.Timeout

abstract class Api[L](pm: PathMatcher[L]) {

  implicit val timeout = Timeout(5L, TimeUnit.SECONDS)

  private var _route: Route = _

  protected def serve(f: Route) = {
    _route = pathPrefix(pm).tapply(_ => f)
  }

  def route = _route

}

object Api {
  implicit def toRouteConcatentation[L](api: Api[L]): RouteWithConcatenation = api.route
  implicit def toRoute[L](api: Api[L]): Route = api.route
}
