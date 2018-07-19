package net.treywood.actor

import akka.NotUsed
import akka.actor.{Actor, Props}
import akka.stream.scaladsl.Source
import net.treywood.actor.ToDoActor.{DeleteItem, FetchItem, NewItem, ToggleDone}
import net.treywood.http.apis.GraphQLApi
import net.treywood.http.apis.ToDoApi.ToDoItem

import scala.collection.mutable
import scala.util.Random

object ToDoActor {

  case class NewItem(label: String)
  case class ToggleDone(id: String, done: Boolean)
  case class DeleteItem(id: String)
  case class FetchItem(id: String)

  def addItem(label: String) = {
    val newId = Random.alphanumeric.take(10).mkString
    val newItem = ToDoItem(newId, label)

    newQueue.enqueue(newItem)
    GraphQLApi.notify("newItem")

    items += (newId -> newItem)
    GraphQLApi.notify("todos")
    newItem
  }

  def deleteItem(id: String) = {
    items -= id
    GraphQLApi.notify("todos")
  }

  def toggleDone(id: String, done: Boolean) = {
    val item = items.get(id).map({ item =>
      val updated = item.copy(done = done)
      items = items.updated(id, updated)
      udpateQueue.enqueue(updated)
      GraphQLApi.notify("updatedItem")
      updated
    })
    item
  }

  def fetchItem(id: String) = {
    items.get(id)
  }

  def getAll = items.values.toSeq

  private val newQueue = mutable.Queue.empty[ToDoItem]
  private val udpateQueue = mutable.Queue.empty[ToDoItem]
  private var items = Map.empty[String, ToDoItem]

  class DequeueIterator[A](queue: mutable.Queue[A]) extends Iterator[Option[A]] {
    def hasNext = queue.nonEmpty
    def next = Option(queue.dequeue())
  }

  def newItems =
    if (newQueue.isEmpty) Source.single(None)
    else Source.fromIterator(() => new DequeueIterator(newQueue)).mapMaterializedValue(_ => NotUsed)

  def updatedItems =
    if (udpateQueue.isEmpty) Source.single(None)
    else Source.fromIterator(() => new DequeueIterator(udpateQueue)).mapMaterializedValue(_ => NotUsed)

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
