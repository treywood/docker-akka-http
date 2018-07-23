package net.treywood.actor

import akka.actor.{Actor, ActorRef}
import net.treywood.graphql.GraphQLExecutor
import net.treywood.http.JsonSupport
import net.treywood.http.apis.GraphQLApi.timeout
import spray.json.{JsObject, _}

import scala.collection.mutable
import scala.concurrent.Await

object GraphQLActor {
  case class StringQuery(str: String)
  case class JsonQuery(json: JsObject)

  case object Subscribe
  case object Unsubscribe

  case class Notify(field: String)
  case class NotifyEnd(field: String)
}

class GraphQLActor extends Actor with JsonSupport {
  import GraphQLActor._

  private val subs: mutable.Set[ActorRef] = mutable.Set.empty

  def receive = {

    case StringQuery(str) =>
      sender ! Await.result(GraphQLExecutor.executeQuery(str.parseJson.asJsObject), timeout.duration)

    case JsonQuery(json) =>
      sender ! Await.result(GraphQLExecutor.executeQuery(json), timeout.duration)

    case Subscribe =>
      subs += sender

    case Unsubscribe =>
      subs -= sender

    case Notify(field) =>
      subs.foreach(_ ! Notify(field))

    case _ => sender ! """{"status":"dunno"}"""
  }

}
