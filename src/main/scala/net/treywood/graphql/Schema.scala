package net.treywood.graphql

import java.util.concurrent.TimeUnit

import akka.pattern.ask
import akka.stream.scaladsl.Source
import akka.util.Timeout
import net.treywood.actor.ToDoActor
import net.treywood.http.apis.ToDoApi.{ToDoItem, ToDoItemType}
import sangria.schema.{Action, Argument, BooleanType, Field, ListType, ObjectType, OptionType, OutputType, StringType, Value, fields}
import sangria.streaming.akkaStreams._

object Schema {

  import net.treywood.http.Main.materializer
  import net.treywood.http.Main.system.dispatcher

  implicit val timeout = Timeout(5L, TimeUnit.SECONDS)

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

  lazy val Mutation =
    ObjectType(
      "Mutation",
      () => fields[Context, Unit](
        Field("createToDoItem", ToDoItemType,
          arguments = labelArg :: Nil,
          resolve = ctx => {
            val label = ctx arg labelArg
            (Context.toDoActor ? ToDoActor.NewItem(label)).mapTo[ToDoItem]
          }),
        Field("deleteToDoItem", BooleanType,
          arguments = idArg :: Nil,
          resolve = ctx => {
            val id = ctx arg idArg
            (Context.toDoActor ? ToDoActor.DeleteItem(id)).map(_ => true)
          }),
        Field("updateToDoItem", OptionType(ToDoItemType),
          arguments = idArg :: doneArg :: Nil,
          resolve = ctx => {
            val id = ctx arg idArg
            val done = ctx arg doneArg
            (Context.toDoActor ? ToDoActor.ToggleDone(id, done)).mapTo[Option[ToDoItem]]
          }
        )
      )
    )

  lazy val Subscription = ObjectType(
    "Subscription",
    () => fields[Context, Unit](
      Field.subs("newItem", ToDoItemType, resolve = _ => ToDoItemAdded.subscription),
      Field.subs("updatedItem", ToDoItemType, resolve = _ => ToDoItemUpdated.subscription),
      Field.subs("deletedItem", ToDoItemType, resolve = _ => ToDoItemDeleted.subscription)
    )
  )

  lazy val Schema = sangria.schema.Schema(Query, mutation = Some(Mutation), subscription = Some(Subscription))

  case class SubscriptionMessage[Ctx, Out](fieldType: OutputType[Out], value: Action[Ctx, Out])

}
