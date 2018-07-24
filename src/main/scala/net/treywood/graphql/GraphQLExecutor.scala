package net.treywood.graphql

import net.treywood.http.JsonSupport
import net.treywood.http.apis.ws.GraphQLWebSocket.Query
import sangria.execution.{ExecutionScheme, Executor}
import sangria.parser.QueryParser
import spray.json.{JsObject, JsString}

import scala.util.{Failure, Success}

object GraphQLExecutor extends JsonSupport {
  import net.treywood.http.Main.system.dispatcher

  def executeQuery(queryStr: String, variablesJson: JsObject)(implicit scheme: ExecutionScheme): scheme.Result[Context, Any] = {
    QueryParser.parse(queryStr) match {
      case Success(query) =>
        executeQuery(Query(query, variablesJson))
      case Failure(e: Throwable) =>
        println(e.getMessage)
        scheme.failed[Context,Any](e)
    }
  }

  def executeQuery(json: JsObject)(implicit scheme: ExecutionScheme): scheme.Result[Context, Any] = {
    val jsonFields = json.fields
    jsonFields.get("query").collect({
      case JsString(queryStr) =>
        val variablesJson = jsonFields.get("variables").collect({
          case x: JsObject => x
        }).getOrElse(JsObject.empty)

        executeQuery(queryStr, variablesJson)
    }).getOrElse({
      scheme.failed[Context,Any](new Error("No Query Found"))
    })
  }

  def executeQuery(query: Query)(implicit scheme: ExecutionScheme): scheme.Result[Context, Any] = {
    val variableMap = query.variables.fields.mapValues(_.convertTo[Any])
    val variables = sangria.marshalling.InputUnmarshaller.mapVars(variableMap)

    println("gonna execute")

    Executor.execute(Schema.Schema, query.astQuery, new Context, variables = variables)
  }

}
