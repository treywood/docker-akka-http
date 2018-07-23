package net.treywood.http.apis.ws

import akka.NotUsed
import akka.actor.{Actor, ActorRef, PoisonPill, Props, Terminated}
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import net.treywood.actor.GraphQLActor.{Notify, Subscribe, Unsubscribe}
import net.treywood.graphql.GraphQLExecutor
import net.treywood.http.apis.GraphQLApi
import net.treywood.http.apis.GraphQLApi.timeout
import net.treywood.http.{JsonSupport, Main}
import sangria.ast.{Document, Field, OperationType}
import sangria.parser.QueryParser
import spray.json.{JsObject, JsString, JsValue, _}

import scala.collection.mutable
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

    private val subs: mutable.Map[String, Subscription] = mutable.Map.empty
    private def subsByField =
      subs.foldLeft(Map.empty[String, Set[Subscription]])({
        case (map, (_, sub)) =>
          val fields = sub.query.astQuery.operations.flatMap({
            case (_, op) => op.selections.collect({ case f: Field => f.name })
          })
          fields.foldLeft(map)({
            case (m, f) =>
              val set = map.getOrElse(f, Set.empty[Subscription])
              m.updated(f, set + sub)
          })
      })

    def printSubs() = {
      subsByField.foreach({
        case (field, ss) => println(s"$field: ${ss.size}")
      })
    }

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

                      subs ++= astQuery.operations.collect({
                        case (_, op) if op.operationType == OperationType.Subscription =>

                          val variables = payloadFields.getOrElse("variables", JsObject.empty).asJsObject
                          val defs = (op :: astQuery.fragments.values.toList).toVector

                          val query = Query(Document(defs), variables)
                          val sub = Subscription(id, query)

                          val resultJson =
                            GraphQLExecutor.executeQuery(query)
                              .map(_.toJson).map(sub.wrap(_).compactPrint)
                          ref ! Await.result(resultJson, timeout.duration)

                          id -> sub
                      })
                      printSubs()
                      graphQLActor ! Subscribe

                    case Failure(e) =>
                      ref ! s"""{"type":"error","message":"${e.getMessage}"}"""
                  }
                case _ =>
                  ref ! """{"type":"error","message":"Invalid Query"}"""
              })
            })

          case (Some(JsString("stop")), Some(JsString(id))) =>
            subs -= id
            printSubs()

          case _ => ref ! """{"type":"dunno"}"""
        }

      case Terminated(subject) =>
        println("TERMINATED")
        printSubs()
        self ! PoisonPill

      // response from GraphQLActor
      case Notify(field) =>
        for {
          subs <- subsByField.get(field)
          sub <- subs
        } ref ! sub.execute.compactPrint

      case PoisonPill =>
        graphQLActor ! Unsubscribe

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

  case class Query(astQuery: Document, variables: JsObject) {
    override def toString: String = astQuery.toString + ":" + variables.compactPrint
  }
  case class Subscription(id: String, query: Query) extends JsonSupport {

    def wrap(json: JsValue, `type`: String = "data") =
      JsObject(Map(
        "id" -> JsString(id),
        "payload" -> json,
        "type" -> JsString(`type`)
      ))

    def execute = {
      val result = GraphQLExecutor.executeQuery(query)
      Await.result(result.map(r => wrap(r.toJson)), timeout.duration)
    }
  }

}
