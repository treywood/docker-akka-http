package net.treywood.http.apis

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.PathMatcher

import akka.http.scaladsl.server.Directives._

abstract class Api[L](val pm: PathMatcher[L]) {

  def route: Route

}

object Api {

  implicit def toRoute[L](api: Api[L]) =
    pathPrefix(api.pm) {
      api.route
    }

  def route = List(NameApi).reduce({
    case (acc, api) => acc.route ~ api.route
  })

}
