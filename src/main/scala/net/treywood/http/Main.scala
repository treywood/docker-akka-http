package net.treywood.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

object Main extends App {

  override def main(args: Array[String]): Unit = {

    implicit val system: ActorSystem = ActorSystem("http-server")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    import system.dispatcher

    val routes =
      get {
        path("hello") {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Hello World</h1>"))
        }
      } ~ pathPrefix("") {
        pathEndOrSingleSlash {
          getFromFile("app/webapp/index.html")
        } ~
        getFromDirectory("app/webapp")
      }

    Http().bindAndHandle(routes, "0.0.0.0", 8080)
    println("Server running")
  }

}
