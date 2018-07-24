package net.treywood.actor

import akka.NotUsed
import akka.actor.{Actor, ActorRef}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import net.treywood.http.apis.GraphQLApi
import net.treywood.http.apis.ToDoApi.ToDoItem

import scala.collection.mutable
import scala.util.Random

object ToDoActor {

  case class NewItem(label: String)
  case class ToggleDone(id: String, done: Boolean)
  case class DeleteItem(id: String)
  case class FetchItem(id: String)

  private var items = Map.empty[String, ToDoItem]

  def getAll = items.values.toSeq

  private val newItemSubs = mutable.Set.empty[ActorRef]
  private val updatedItemSubs = mutable.Set.empty[ActorRef]

  def newItems: Source[Option[ToDoItem], NotUsed] =
    Source.actorRef[Option[ToDoItem]](0, OverflowStrategy.fail)
      .mapMaterializedValue(ref => {
        newItemSubs += ref
        NotUsed
      })

  def updatedItems: Source[Option[ToDoItem], NotUsed] =
    Source.actorRef(0, OverflowStrategy.fail)
      .mapMaterializedValue(ref => {
        updatedItemSubs += ref
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
  }

  private def addItem(label: String) = {
    val newId = Random.alphanumeric.take(10).mkString
    val newItem = ToDoItem(newId, label)

    newItemSubs.foreach(_ ! Option(newItem))
    items += (newId -> newItem)
    newItem
  }

  private def deleteItem(id: String) = {
    items -= id
  }

  private def toggleDone(id: String, done: Boolean): Option[ToDoItem] = {
    val item = items.get(id).map({ item =>
      val updated = item.copy(done = done)
      items = items.updated(id, updated)
      updatedItemSubs.foreach(_ ! Option(updated))
      updated
    })
    item
  }

  private def fetchItem(id: String) = ToDoActor.items.get(id)

}
