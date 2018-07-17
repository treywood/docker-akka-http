package net.treywood.http.apis

import akka.actor.{Actor, ActorRef, Props}
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, Sink, Source}
import net.treywood.graphql.{Context, Schema}
import net.treywood.http.JsonSupport
import sangria.execution.{ExceptionHandler, Executor, HandledException}
import sangria.parser.QueryParser
import spray.json.{JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString, JsValue, JsonFormat, _}

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}


object GraphQLApi extends Api("graphql") with JsonSupport {
  import GraphQLActor._
  import net.treywood.http.Main.system.dispatcher

  implicit val variablesSupport = new JsonFormat[Any] {
    override def write(obj: Any): JsValue = obj match {
      case str: String => JsString(str)
      case n: BigDecimal => JsNumber(n)
      case b: Boolean => JsBoolean(b)
      case m: Map[_,_] => JsObject(m.map({ case (k, v) => k.toString -> write(v) }))
      case a: Iterable[_] => JsArray(a.map(write).toVector)
      case null => JsNull
    }

    override def read(json: JsValue): Any = json match {
      case JsString(str) => str
      case JsNumber(n) => n
      case JsBoolean(b) => b
      case JsObject(fields) => fields.mapValues(read)
      case JsNull => null
      case JsArray(items) => items.map(read)
    }
  }

  def failure = Future.successful(HttpResponse(StatusCodes.BadRequest))

  serve {
    get { ctx =>
      val result = ctx.request.header[UpgradeToWebSocket] match {
        case Some(upgrade) => upgrade.handleMessages(graphQLSubscription, subprotocol = Some("graphql-ws"))
        case None => HttpResponse(StatusCodes.NotFound)
      }
      ctx.complete(result)
    } ~
    (post & entity(as[JsValue])) {
      json =>
        val result = (graphqlActor ? JsonQuery(json.asJsObject)).map({
          case Response(res) =>
            HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, res))
          case x =>
            println(s"THIS: $x")
            HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, x.toString))
        })
        complete(result)
    }
  }

  lazy val graphqlActor = net.treywood.http.Main.system.actorOf(Props[GraphQLActor])

  object GraphQLActor {
    case class StringQuery(str: String)
    case class JsonQuery(json: JsObject)
    case class Response(str: String)

    case class NewSubscription(payload: JsObject)
  }

  class GraphQLActor extends Actor with JsonSupport {
    import GraphQLActor._

    var subs: Map[String, Set[ActorRef]] = Map.empty
    var operations: Map[String, JsObject] = Map.empty

    def receive = {

      case StringQuery(str) =>
        sender ! executeQuery(str.parseJson.asJsObject).map(r => Response(r.toString))

      case JsonQuery(json) =>
        sender ! executeQuery(json).map(r => Response(r.toString))

      case NewSubscription(payload) =>
        payload.fields.get("operationName").foreach({
          case JsString(opName) =>
            println(opName)

            val opSubs = subs.getOrElse(opName, Set.empty)
            subs = subs.updated(opName, opSubs + sender)
            operations = operations.updated(opName, payload)
            context.watch(sender)

            val result = Await.result(executeQuery(payload), timeout.duration)
            sender ! result.toJson.compactPrint
          case x => sender ! x.toString
        })

      case _ => sender ! """{"status":"dunno"}"""
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

          val variableMap = variablesJson.fields.mapValues(_.convertTo[Any])
          val variables = sangria.marshalling.InputUnmarshaller.mapVars(variableMap)

          println("gonna execute")

          Executor.execute(
            Schema.Schema, query, Context(),
            variables = variables
          )

        case Failure(e: Throwable) =>
          println(e.getMessage)
          Future.successful(e.getMessage)
      }
    }
  }

  val graphQLSubscription =
      Flow[Message]
        .collect({
          case TextMessage.Strict(txt) =>
            println(txt)
            val json = txt.parseJson.asJsObject
            val msg = for {
              JsString(typ) <- json.fields.get("type") if typ == "start"
              payload <- json.fields.get("payload")
            } yield NewSubscription(payload.asJsObject)
          msg.getOrElse(())
        })
        .ask[String](parallelism = 5)(graphqlActor)
        .map(str => TextMessage(Source.single(str)))

}
