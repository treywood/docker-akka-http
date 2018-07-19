package net.treywood.http.apis

import akka.NotUsed
import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import net.treywood.graphql.{Context, Schema}
import net.treywood.http.{JsonSupport, Main}
import sangria.ast.{Document, Field, OperationType}
import sangria.execution.Executor
import sangria.parser.QueryParser
import spray.json.{JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString, JsValue, JsonFormat, _}

import scala.concurrent.{Await, Future}
import scala.util.parsing.json
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
        case Some(upgrade) => upgrade.handleMessages(newSubscription, subprotocol = Some("graphql-ws"))
        case None => HttpResponse(StatusCodes.NotFound)
      }
      ctx.complete(result)
    } ~
    (post & entity(as[JsValue])) {
      json =>
        val result = (graphqlActor ? JsonQuery(json.asJsObject)).map({
          res =>
            val json = res.toJson.compactPrint
            HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, json))
        })
        complete(result)
    }
  }

  lazy val graphqlActor = net.treywood.http.Main.system.actorOf(Props[GraphQLActor])

  def notify(field: String) = {
    graphqlActor ! Notify(field)
  }

  case class Query(id: String, doc: Document, variables: JsObject)

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

            val queryResult = Await.result(executeQuery(sub), timeout.duration)
            push(newSub, queryResult.toJson.asJsObject)
        })

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

  object WsActor {
    case class NewSubscription(ref: ActorRef)
  }

  class WsActor extends Actor with JsonSupport {
    import WsActor._

    def waiting: Receive = {
      case NewSubscription(ref) =>
        println("connected")
        context become connected(ref)
    }

    def connected(ref: ActorRef): Receive = {

      // from WebSocket
      case TextMessage.Strict(txt) =>
        println(s"received message: $txt")

        val fields = txt.parseJson.asJsObject.fields
        (fields.get("type"), fields.get("id")) match {
          // connection acknowledgement
          case (Some(JsString("connection_init")), _) => ref ! """{"type":"connection_ack"}"""

          // begin a subscription
          case (Some(JsString("start")), Some(JsString(id))) =>
            fields.get("payload").foreach(payload => {
              val payloadFields = payload.asJsObject.fields
              payloadFields.get("query").foreach({
                case JsString(queryStr) =>

                  QueryParser.parse(queryStr) match {
                    case Success(query) =>

                      // find all of the fields and map to queries to associate
                      // with this websocket
                      val subs = query.operations.collect({
                        case (_, op) if op.operationType == OperationType.Subscription =>
                          op.selections.collect({
                            case f: Field =>
                              val defs = op :: query.fragments.values.toList
                              val variables = payloadFields.getOrElse("variables", JsObject.empty).asJsObject
                              f.name -> Query(id, Document(defs.toVector), variables)
                          })
                      }).flatten.toMap

                      // subscribe the queries to the graphql actor
                      graphqlActor ! GraphQLActor.NewSubscription(subs)

                    case Failure(e) =>
                      ref ! s"""{"type":"error","message":"${e.getMessage}"}"""
                  }
                case _ =>
                  ref ! """{"type":"error","message":"Invalid Query"}"""
              })
            })

          case _ => ref ! """{"type":"dunno"}"""
        }

      // response from GraphQLActor
      case str: String => ref ! str

    }

    def receive = waiting

  }

  def newSubscription = {
    val actor = Main.system.actorOf(Props[WsActor])

    val incoming =
      Sink.actorRef(actor, PoisonPill)

    val outgoing: Source[TextMessage.Strict, NotUsed] =
      Source.actorRef[String](10, OverflowStrategy.fail)
        .mapMaterializedValue({ ref =>
          actor ! WsActor.NewSubscription(ref)
          NotUsed
        })
        .map(str => TextMessage.Strict(str))

    Flow.fromSinkAndSource(incoming, outgoing)
  }

  case class GraphQLSubscription(ref: ActorRef, query: Query)

}
