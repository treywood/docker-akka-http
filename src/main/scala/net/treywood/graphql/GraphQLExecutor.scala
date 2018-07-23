package net.treywood.graphql

import net.treywood.http.JsonSupport
import net.treywood.http.apis.ws.GraphQLWebSocket.Query
import sangria.execution.Executor
import sangria.parser.QueryParser
import spray.json.{JsObject, JsString}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object GraphQLExecutor extends JsonSupport {
  import net.treywood.http.Main.system.dispatcher

  def executeQuery(queryStr: String, variablesJson: JsObject): Future[Any] = {
    QueryParser.parse(queryStr) match {
      case Success(query) =>
        executeQuery(Query(query, variablesJson))
      case Failure(e: Throwable) =>
        println(e.getMessage)
        Future.successful(e.getMessage)
    }
  }

  def executeQuery(json: JsObject): Future[Any] = {
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

  def executeQuery(query: Query): Future[Any] = {
    val variableMap = query.variables.fields.mapValues(_.convertTo[Any])
    val variables = sangria.marshalling.InputUnmarshaller.mapVars(variableMap)

    println("gonna execute")

    Executor.execute(Schema.Schema, query.astQuery, new Context, variables = variables)
  }

}
