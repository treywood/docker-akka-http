package net.treywood.http.apis

import akka.NotUsed
import akka.actor.{Actor, ActorRef, PoisonPill, Props, Terminated}
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import net.treywood.actor.{GraphQLActor, Query}
import net.treywood.http.{JsonSupport, Main}
import sangria.ast.{Document, Field, OperationType}
import sangria.parser.QueryParser
import spray.json.{JsObject, JsString, JsValue, _}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object GraphQLApi extends Api("graphql") with JsonSupport {
  import GraphQLActor._
  import net.treywood.http.Main.system.dispatcher

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

  object WsActor {
    case class NewSubscription(ref: ActorRef)
  }

  class WsActor extends Actor with JsonSupport {
    import WsActor._

    def waiting: Receive = {
      case NewSubscription(ref) =>
        println("connected")
        context watch ref
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
                              // new operation with only this field
                              // val subOp = op.copy(selections = Vector(f))

                              // include fragments just in case
                              val defs = Vector(op :: query.fragments.values.toList :_*)

                              val variables = payloadFields.getOrElse("variables", JsObject.empty).asJsObject
                              f.name -> Query(id, Document(defs), variables)
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

      case Terminated(subject) =>
        println("TERMINATED")

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

}
