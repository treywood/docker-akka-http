package net.treywood.http.apis

import akka.actor.Props
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import net.treywood.actor.GraphQLActor
import net.treywood.http.JsonSupport
import net.treywood.http.apis.ws.GraphQLWebSocket
import spray.json.{JsValue, _}

import scala.concurrent.Future

object GraphQLApi extends Api("graphql") with JsonSupport {
  import GraphQLActor._
  import net.treywood.http.Main.system.dispatcher

  def failure = Future.successful(HttpResponse(StatusCodes.BadRequest))

  serve {
    get { ctx =>
      val result = ctx.request.header[UpgradeToWebSocket] match {
        case Some(upgrade) => upgrade.handleMessages(GraphQLWebSocket.newSubscription, subprotocol = Some("graphql-ws"))
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

}
