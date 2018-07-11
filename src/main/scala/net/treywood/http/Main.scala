package net.treywood.http

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

object Main extends App {

  override def main(args: Array[String]): Unit = {

    implicit val system: ActorSystem = ActorSystem("http-server")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    implicit val dispatcher: ExecutionContextExecutor = system.dispatcher

    val routes = get {
      path("hello") {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Hello World</h1>"))
      }
    }

    val binding = Http().bindAndHandle(routes, "localhost", 8080)

    println("Server running")
    StdIn.readLine()

    binding.flatMap(_.unbind()).onComplete(_ => system.terminate())
  }

}
