package net.treywood.graphql

import akka.NotUsed
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import net.treywood.http.apis.ToDoApi.ToDoItem
import sangria.schema.{Action, Value}

trait SubscriptionType[A] {
  import net.treywood.http.Main.materializer

  protected lazy val (queue, source) = Source.queue[A](10, OverflowStrategy.backpressure).preMaterialize()
  private def action[T](x: T): Action[Context, T] = Value[Context, T](x)

  def next(x: A) = queue.offer(x)
  def subscription: Source[Action[Context, A], NotUsed] = source.map(action[A])
}

object ToDoItemAdded extends SubscriptionType[ToDoItem]
object ToDoItemUpdated extends SubscriptionType[ToDoItem]
object ToDoItemDeleted extends SubscriptionType[ToDoItem]