package net.treywood.actor

import akka.actor.Actor
import net.treywood.graphql.{ToDoItemAdded, ToDoItemDeleted, ToDoItemUpdated}
import net.treywood.http.apis.ToDoApi.ToDoItem

import scala.util.Random

object ToDoActor {

  case class NewItem(label: String)
  case class ToggleDone(id: String, done: Boolean)
  case class DeleteItem(id: String)
  case class FetchItem(id: String)

  private var items = Map.empty[String, ToDoItem]

  def getAll = items.values.toSeq
}

class ToDoActor extends Actor {
  import ToDoActor._

  def receive = {
    case NewItem(label) => sender ! addItem(label)
    case DeleteItem(id) => sender ! deleteItem(id)
    case ToggleDone(id, done) => sender ! toggleDone(id, done)
    case FetchItem(id) => sender ! fetchItem(id)
  }

  private def addItem(label: String) = {
    val newId = Random.alphanumeric.take(10).mkString
    val newItem = ToDoItem(newId, label)

    ToDoItemAdded.next(newItem)
    items += (newId -> newItem)
    newItem
  }

  private def deleteItem(id: String) = {
    items.get(id).foreach(ToDoItemDeleted.next)
    items -= id
  }

  private def toggleDone(id: String, done: Boolean): Option[ToDoItem] = {
    val item = items.get(id).map({ item =>
      val updated = item.copy(done = done)
      items = items.updated(id, updated)
      ToDoItemUpdated.next(updated)
      updated
    })
    item
  }

  private def fetchItem(id: String) = ToDoActor.items.get(id)

}
