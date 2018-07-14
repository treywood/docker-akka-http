package net.treywood.http.apis

import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._

object GreetApi extends Api("api" / "greet") {

  serve {
    get {
      parameter("name".?) { maybeName =>
        val name = maybeName.getOrElse("you")
        complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"hello $name"))
      }
    }
  }

}
