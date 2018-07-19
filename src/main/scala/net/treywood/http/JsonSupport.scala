package net.treywood.http

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import net.treywood.http.apis.ToDoApi.ToDoItem
import spray.json.{JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString, JsValue, JsonFormat}

trait JsonSupport extends SprayJsonSupport {

  import spray.json.DefaultJsonProtocol._

  implicit val toDoItemSupport = jsonFormat3(ToDoItem)

  implicit val variablesSupport = new JsonFormat[Any] {
    override def write(obj: Any): JsValue = obj match {
      case str: String => JsString(str)
      case n: BigDecimal => JsNumber(n)
      case n: Integer => JsNumber(n)
      case n: BigInt => JsNumber(n)
      case n: Double => JsNumber(n)
      case n: Float => JsNumber(n)
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

}
