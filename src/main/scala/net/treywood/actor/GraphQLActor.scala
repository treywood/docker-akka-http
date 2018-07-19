package net.treywood.actor

import akka.actor.{Actor, ActorRef}
import net.treywood.graphql.{Context, Schema}
import net.treywood.http.JsonSupport
import net.treywood.http.apis.GraphQLApi.timeout
import sangria.ast.Document
import sangria.execution.Executor
import sangria.parser.QueryParser
import spray.json.{JsObject, JsString, _}

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

object GraphQLActor {
  type SubMap = Map[String, Query]

  case class StringQuery(str: String)
  case class JsonQuery(json: JsObject)
  case class Result(str: String)

  case class NewSubscription(subs: SubMap)
  case class Notify(opName: String)
}

class GraphQLActor extends Actor with JsonSupport {
  import GraphQLActor._
  import net.treywood.http.Main.system.dispatcher

  var subs: Map[String, Set[GraphQLSubscription]] = Map.empty

  def receive = {

    case StringQuery(str) =>
      sender ! Await.result(executeQuery(str.parseJson.asJsObject), timeout.duration)

    case JsonQuery(json) =>
      sender ! Await.result(executeQuery(json), timeout.duration)

    case NewSubscription(subscriptions) =>
      subscriptions.foreach({
        case (field, sub) =>
          val fieldSubs = subs.getOrElse(field, Set.empty)

          val newSub = GraphQLSubscription(sender, sub)
          subs += (field -> (fieldSubs + newSub))
      })
      println(s"${subs.size} SUBSCRIPTIONS")

    case Notify(field) => for {
      subscriptions <- subs.get(field)
      sub <- subscriptions
    } {
      val queryResult = Await.result(executeQuery(sub.query), timeout.duration)
      push(sub, queryResult.toJson.asJsObject)
    }

    case _ => sender ! """{"status":"dunno"}"""
  }

  private def push(sub: GraphQLSubscription, result: JsObject) = {
    val payload =
      JsObject(Map(
        "type" -> JsString("data"),
        "id" -> JsString(sub.query.id),
        "payload" -> result
      ))
    sub.ref ! payload.compactPrint
  }

  private def executeQuery(query: Query): Future[Any] = {
    val variableMap = query.variables.fields.mapValues(_.convertTo[Any])
    val variables = sangria.marshalling.InputUnmarshaller.mapVars(variableMap)

    println("gonna execute")

    Executor.execute( Schema.Schema, query.doc, Context(), variables = variables)
  }

  private def executeQuery(json: JsObject): Future[Any] = {
    val jsonFields = json.fields
    jsonFields.get("query").collect({
      case JsString(queryStr) =>
        val variablesJson = jsonFields.get("variables").collect({
          case x: JsObject => x
        }).getOrElse(JsObject.empty)

        executeQuery(queryStr, variablesJson)
      case x => Future.successful(x)
    }).getOrElse(Future.failed(new Exception("invalid query json")))
  }

  private def executeQuery(queryStr: String, variablesJson: JsObject): Future[Any] = {
    QueryParser.parse(queryStr) match {
      case Success(query) =>
        executeQuery(Query("", query, variablesJson))
      case Failure(e: Throwable) =>
        println(e.getMessage)
        Future.successful(e.getMessage)
    }
  }
}

case class GraphQLSubscription(ref: ActorRef, query: Query) {
  lazy val hash = s"${query.doc.hashCode}:${query.variables.hashCode}"
}

case class Query(id: String, doc: Document, variables: JsObject)
