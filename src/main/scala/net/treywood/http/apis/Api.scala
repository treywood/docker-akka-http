package net.treywood.http.apis

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteConcatenation.RouteWithConcatenation
import akka.http.scaladsl.server.{PathMatcher, Route}

abstract class Api[L](pm: PathMatcher[L]) {

  private var _route: Route = _

  protected def serve(f: Route) = {
    _route = pathPrefix(pm).tapply(_ => f)
  }

  def route = _route

}

object Api {
  implicit def toRoute[L](api: Api[L]): RouteWithConcatenation = api.route
}
