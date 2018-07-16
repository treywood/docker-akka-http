package net.treywood.http.apis

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import net.treywood.graphql.{Context, Schema}
import net.treywood.http.JsonSupport
import sangria.execution.Executor
import sangria.parser.{QueryParser, SyntaxError}
import spray.json.{JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString, JsValue, JsonFormat}
import spray.json._

import scala.concurrent.Future
import scala.util.{Failure, Success}


object GraphQLApi extends Api("graphql") with JsonSupport {

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
    (post & entity(as[JsValue])) {
      json =>

        val result = json.asJsObject.fields.get("query").collect({
          case JsString(queryStr) => QueryParser.parse(queryStr) match {
            case Success(query) =>

              val variableMap = json.asJsObject.fields.get("variables").collect({
                case JsObject(fields) => fields.mapValues(_.convertTo[Any])
              }).getOrElse(Map.empty[String, Any])
              val variables = sangria.marshalling.InputUnmarshaller.mapVars(variableMap)

              Executor.execute(
                Schema.Schema, query, Context(),
                variables = variables
              ).map({ result =>
                val response = result.toJson.compactPrint
                HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, response))
              })

            case Failure(_: SyntaxError) => failure
            case Failure(e: Throwable) => throw e
          }
        }).getOrElse(failure)

        complete(result)
    }
  }

}
