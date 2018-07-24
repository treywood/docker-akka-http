package net.treywood.actor

import akka.actor.Actor
import net.treywood.graphql.GraphQLExecutor
import net.treywood.http.JsonSupport
import net.treywood.http.apis.GraphQLApi.timeout
import spray.json.{JsObject, _}

import scala.concurrent.Await

object GraphQLActor {
  case class StringQuery(str: String)
  case class JsonQuery(json: JsObject)
}

class GraphQLActor extends Actor with JsonSupport {
  import GraphQLActor._
  import sangria.execution.ExecutionScheme.Default

  def receive = {

    case StringQuery(str) =>
      sender ! Await.result(GraphQLExecutor.executeQuery(str.parseJson.asJsObject), timeout.duration)

    case JsonQuery(json) =>
      sender ! Await.result(GraphQLExecutor.executeQuery(json), timeout.duration)

    case _ => sender ! """{"status":"dunno"}"""
  }

}
