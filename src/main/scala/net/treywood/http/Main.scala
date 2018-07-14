package net.treywood.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
//import net.treywood.http.apis.Api._
import net.treywood.http.apis.GreetApi

object Main extends App {

  lazy val PORT = 8080

  override def main(args: Array[String]): Unit = {

    implicit val system: ActorSystem = ActorSystem("http-server")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    import system.dispatcher

    val routes =
      GreetApi ~ pathPrefix("") {
        pathEndOrSingleSlash {
          getFromFile("app/webapp/index.html")
        } ~
        getFromDirectory("app/webapp")
      }

    Http().bindAndHandle(routes, "0.0.0.0", PORT)
    println("Server running")
  }

}
