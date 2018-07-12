package net.treywood.http
package apis

import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._

object NameApi extends Api("api" / "name") {

  def route = get {
    pathEndOrSingleSlash {
      parameter("is".?) { maybeName =>
        val name = maybeName.getOrElse("Trey")
        complete(HttpEntity(ContentTypes.`application/json`, s"""{"name":"$name"}"""))
      }
    }
  }

}
