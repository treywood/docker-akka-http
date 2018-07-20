package net.treywood.actor

import akka.NotUsed
import akka.actor.Actor
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import net.treywood.actor.GraphQLActor.{Notify, NotifyEnd}
import net.treywood.http.apis.GraphQLApi
import net.treywood.http.apis.ToDoApi.ToDoItem

import scala.collection.mutable
import scala.util.Random

object ToDoActor {

  case class NewItem(label: String)
  case class ToggleDone(id: String, done: Boolean)
  case class DeleteItem(id: String)
  case class FetchItem(id: String)

  private val newQueue = mutable.Queue.empty[ToDoItem]
  private val updateQueue = mutable.Queue.empty[ToDoItem]

  private var items = Map.empty[String, ToDoItem]

  def getAll = items.values.toSeq

  def newItems: Source[Option[ToDoItem], NotUsed] =
    Source.queue[Option[ToDoItem]](0, OverflowStrategy.backpressure)
      .mapMaterializedValue({ queue =>
        newQueue.foreach(x => queue.offer(Option(x)))
        NotUsed
      })

  def updatedItems: Source[Option[ToDoItem], NotUsed] =
    Source.queue[Option[ToDoItem]](0, OverflowStrategy.backpressure)
      .mapMaterializedValue({ queue =>
        updateQueue.foreach(x => queue.offer(Option(x)))
        NotUsed
      })

}

class ToDoActor extends Actor {
  import ToDoActor._

  def receive = {
    case NewItem(label) => sender ! addItem(label)
    case DeleteItem(id) => sender ! deleteItem(id)
    case ToggleDone(id, done) => sender ! toggleDone(id, done)
    case FetchItem(id) => sender ! fetchItem(id)

    case NotifyEnd(field) => field match {
      case "newItem" => newQueue.clear()
      case "updatedItem" => updateQueue.clear()
    }
  }

  private def addItem(label: String) = {
    val newId = Random.alphanumeric.take(10).mkString
    val newItem = ToDoItem(newId, label)

    newQueue.enqueue(newItem)
    GraphQLApi.graphqlActor ! Notify("newItem")

    items += (newId -> newItem)
    GraphQLApi.graphqlActor ! Notify("todos")
    newItem
  }

  private def deleteItem(id: String) = {
    items -= id
    GraphQLApi.notify("todos")
  }

  private def toggleDone(id: String, done: Boolean) = {
    val item = items.get(id).map({ item =>
      val updated = item.copy(done = done)
      items = items.updated(id, updated)
      updateQueue.enqueue(updated)
      GraphQLApi.graphqlActor ! Notify("updatedItem")
      updated
    })
    item
  }

  private def fetchItem(id: String) = ToDoActor.items.get(id)

}
