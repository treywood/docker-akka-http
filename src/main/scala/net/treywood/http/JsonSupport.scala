package net.treywood.http

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import net.treywood.http.apis.ToDoApi.ToDoItem

trait JsonSupport extends SprayJsonSupport {

  import spray.json.DefaultJsonProtocol._

  implicit val toDoItemSupport = jsonFormat3(ToDoItem)

}
