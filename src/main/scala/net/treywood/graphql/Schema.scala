package net.treywood.graphql

import net.treywood.actor.ToDoActor
import net.treywood.http.apis.ToDoApi.ToDoItemType
import sangria.schema.{Argument, Field, ListType, StringType, BooleanType, OptionType, ObjectType, fields}

object Schema {

  lazy val Query = ObjectType(
    "Query",
    () => fields[Context, Unit](
      Field("todos", ListType(ToDoItemType), resolve = _ => ToDoActor.getAll.toList)
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

  lazy val Schema = sangria.schema.Schema(Query, Some(Mutation))

}
