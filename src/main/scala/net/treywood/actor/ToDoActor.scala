package net.treywood.actor

import akka.actor.{Actor, Props}
import net.treywood.actor.ToDoActor.{DeleteItem, FetchItem, NewItem, ToggleDone}
import net.treywood.http.apis.ToDoApi.ToDoItem

import scala.util.Random

object ToDoActor {

  case class NewItem(label: String)
  case class ToggleDone(id: String, done: Boolean)
  case class DeleteItem(id: String)
  case class FetchItem(id: String)

  def addItem(label: String) = {
    val newId = Random.alphanumeric.take(10).mkString
    val newItem = ToDoItem(newId, label)
    items += (newId -> newItem)

    Thread.sleep(2000)
    newItem
  }

  def deleteItem(id: String) = {
    Thread.sleep(1000)
    items -= id
  }

  def toggleDone(id: String, done: Boolean) = {
    Thread.sleep(500)
    items.get(id).map({ item =>
      val updated = item.copy(done = done)
      items = items.updated(id, updated)
      updated
    })
  }

  def fetchItem(id: String) = {
    Thread.sleep(1000)
    items.get(id)
  }

  def getAll = items.values

  private var items = Map.empty[String, ToDoItem]

  lazy val props = Props[ToDoActor]
}

class ToDoActor extends Actor {

  def receive = {
    case NewItem(label) => sender ! ToDoActor.addItem(label)
    case DeleteItem(id) => sender ! ToDoActor.deleteItem(id)
    case ToggleDone(id, done) => sender ! ToDoActor.toggleDone(id, done)
    case FetchItem(id) => sender ! ToDoActor.fetchItem(id)
  }

}
