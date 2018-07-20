package net.treywood.actor

import akka.actor.{Actor, ActorRef}
import net.treywood.graphql.{Context, Schema}
import net.treywood.http.JsonSupport
import net.treywood.http.apis.GraphQLApi.timeout
import sangria.ast.Document
import sangria.execution.Executor
import sangria.parser.QueryParser
import spray.json.{JsObject, JsString, _}

import scala.collection.mutable
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

object GraphQLActor {
  type SubMap = Map[String, Subscription]

  case class StringQuery(str: String)
  case class JsonQuery(json: JsObject)
  case class Result(str: String)

  case class NewSubscription(subs: SubMap)
  case class StopSubscription(id: String)
  case class Notify(field: String)
  case class NotifyEnd(field: String)
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
      println(s"${subs.values.map(_.size).sum} SUBSCRIPTIONS")

    case StopSubscription(id) =>

      val without = subs.map({
        case (field, set) =>
          val newSet = set.filterNot(s => s.subscription.id == id)
          (field, newSet, newSet.size)
      })

      subs =
        without.filter({ case (_, _, size) => size > 0 })
          .map({ case (f, s, _) => f -> s }).toMap

      println(s"STOPPED $id, NOW ${subs.values.map(_.size).sum} SUBSCRIPTIONS")

    case Notify(field) => for {
      subscriptions <- subs.get(field)
      _ = println(s"${subscriptions.size} subscribed to $field")
      (_, subsByQuery) <- subscriptions.groupBy(s => s.subscription.query.toString)
      query <- subsByQuery.headOption.map(_.subscription.query)
    } {
      println(s"${subsByQuery.size} of ${subscriptions.size} subs will receive")
      val queryResult = Await.result(executeQuery(query), timeout.duration)
      subsByQuery.foreach(s => push(s, queryResult.toJson.asJsObject))
      sender ! NotifyEnd
    }

    case _ => sender ! """{"status":"dunno"}"""
  }

  private def push(sub: GraphQLSubscription, result: JsObject) = {
    val payload =
      JsObject(Map(
        "type" -> JsString("data"),
        "id" -> JsString(sub.subscription.id),
        "payload" -> result
      ))
    sub.ref ! payload.compactPrint
  }

  private def executeQuery(query: Query): Future[Any] = {
    val variableMap = query.variables.fields.mapValues(_.convertTo[Any])
    val variables = sangria.marshalling.InputUnmarshaller.mapVars(variableMap)

    println("gonna execute")

    Executor.execute( Schema.Schema, query.astQuery, new Context, variables = variables)
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
        executeQuery(Query(query, variablesJson))
      case Failure(e: Throwable) =>
        println(e.getMessage)
        Future.successful(e.getMessage)
    }
  }
}

case class GraphQLSubscription(ref: ActorRef, subscription: Subscription)

case class Query(astQuery: Document, variables: JsObject) {
  override def toString: String = astQuery.toString + ":" + variables.compactPrint
}
case class Subscription(id: String, query: Query)
