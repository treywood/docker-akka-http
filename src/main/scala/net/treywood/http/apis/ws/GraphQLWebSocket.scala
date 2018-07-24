package net.treywood.http.apis.ws

import akka.NotUsed
import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import net.treywood.graphql.GraphQLExecutor
import net.treywood.http.apis.GraphQLApi
import net.treywood.http.apis.GraphQLApi.timeout
import net.treywood.http.{JsonSupport, Main}
import sangria.ast.{Document, OperationType}
import sangria.parser.QueryParser
import spray.json.{JsObject, JsString, JsValue, _}

import scala.concurrent.Await
import scala.util.{Failure, Success}

object GraphQLWebSocket {
  import Main.system.dispatcher

  lazy val graphQLActor = GraphQLApi.graphqlActor

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
                    case Success(astQuery) =>

                      astQuery.operations.collect({
                        case (_, op) if op.operationType == OperationType.Subscription =>
                          import Main.materializer
                          import sangria.streaming.akkaStreams._
                          implicit val scheme = sangria.execution.ExecutionScheme.Stream

                          val variables = payloadFields.getOrElse("variables", JsObject.empty).asJsObject
                          val defs = (op :: astQuery.fragments.values.toList).toVector

                          val query = Query(Document(defs), variables)
                          val source = GraphQLExecutor.executeQuery(query).map({ r =>
                            JsObject(Map(
                              "id" -> JsString(id),
                              "type" -> JsString("data"),
                              "payload" -> r.toJson
                            )).compactPrint
                          })
                          source.runWith(Sink.actorRef(ref, PoisonPill))
                      })

                    case Failure(e) =>
                      ref ! s"""{"type":"error","message":"${e.getMessage}"}"""
                  }
                case _ =>
                  ref ! """{"type":"error","message":"Invalid Query"}"""
              })
            })

          case (Some(JsString("stop")), Some(JsString(id))) =>

          case _ => ref ! """{"type":"dunno"}"""
        }

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

  case class Query(astQuery: Document, variables: JsObject)

}
