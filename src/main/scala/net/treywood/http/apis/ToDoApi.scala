package net.treywood.http.apis

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import net.treywood.actor.ToDoActor
import net.treywood.graphql.Context
import net.treywood.http.{JsonSupport, Main}
import sangria.schema.{Field, ObjectType, fields, StringType, BooleanType}
import spray.json.{JsBoolean, JsString, JsValue}

import scala.concurrent.Await

object ToDoApi extends Api("api" / "todo") with JsonSupport {

  lazy val actor = Main.system.actorOf(ToDoActor.props)

  serve {
    (post & entity(as[JsValue])) { json =>
        val newItem = json.asJsObject.fields.get("label").collect({
          case JsString(label) => (actor ? ToDoActor.NewItem(label)).mapTo[ToDoItem]
        })
        rejectEmptyResponse {
          complete(newItem)
        }
    } ~ path(Segment) { id =>
      get {
        val item = (actor ? ToDoActor.FetchItem(id)).mapTo[Option[ToDoItem]]
        rejectEmptyResponse {
          complete(item)
        }
      } ~ (put & entity(as[JsValue])) { json =>
        val updated = json.asJsObject.fields.get("done").collect({
          case JsBoolean(done) => (actor ? ToDoActor.ToggleDone(id, done)).mapTo[Option[ToDoItem]]
        })
        rejectEmptyResponse {
          complete(updated)
        }
      } ~ delete {
        Await.result(actor ? ToDoActor.DeleteItem(id), timeout.duration)
        complete(HttpResponse())
      }
    }
  }

  case class ToDoItem(id: String, label: String, done: Boolean = false)

  lazy val ToDoItemType = ObjectType(
    "ToDoItem",
    () => fields[Context, ToDoItem](
      Field("id", StringType, resolve = _.value.id),
      Field("label", StringType, resolve = _.value.label),
      Field("done", BooleanType, resolve = _.value.done)
    )
  )

}
