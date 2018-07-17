package net.treywood.graphql

import akka.NotUsed
import akka.stream.scaladsl.Source
import net.treywood.actor.ToDoActor
import net.treywood.http.apis.ToDoApi
import net.treywood.http.apis.ToDoApi.{ToDoItem, ToDoItemType}
import sangria.schema.{Action, Argument, BooleanType, Field, ListType, ObjectType, OptionType, OutputType, StringType, Value, fields}
import sangria.streaming.akkaStreams._

object Schema {

  import net.treywood.http.Main.system.dispatcher
  import net.treywood.http.Main.materializer

  lazy val Query = ObjectType(
    "Query",
    () => fields[Context, Unit](
      Field("todos", ListType(ToDoItemType), resolve = _ => {
        println("gettin em")
        ToDoActor.getAll.toList
      })
    )
  )

  lazy val labelArg = Argument("label", StringType)
  lazy val idArg = Argument("id", StringType)
  lazy val doneArg = Argument("done", BooleanType)

  lazy val Mutation = ObjectType(
    "Mutation",
    () => fields[Context, Unit](
      Field("createToDoItem", ToDoItemType,
        arguments = labelArg :: Nil,
        resolve = ctx => {
          val label = ctx arg labelArg
          ToDoActor.addItem(label)
        }),
      Field("deleteToDoItem", BooleanType,
        arguments = idArg :: Nil,
        resolve = ctx => {
          val id = ctx arg idArg
          ToDoActor.deleteItem(id)
          true
        }),
      Field("updateToDoItem", OptionType(ToDoItemType),
        arguments = idArg :: doneArg :: Nil,
        resolve = ctx => {
          val id = ctx arg idArg
          val done = ctx arg doneArg
          ToDoActor.toggleDone(id, done)
        }
      )
    )
  )

  lazy val Subscription = ObjectType(
    "Subscription",
    () => fields[Context, Unit](
      Field.subs("todos", ListType(ToDoItemType),
        resolve = _ =>  Source.
      )
    )
  )

  lazy val Schema = sangria.schema.Schema(Query, mutation = Some(Mutation), subscription = Some(Subscription))

  case class SubscriptionMessage[Ctx, Out](fieldType: OutputType[Out], value: Action[Ctx, Out])

}
