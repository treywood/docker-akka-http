package net.treywood.actor

import akka.NotUsed
import akka.actor.{Actor, ActorRef, Props}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import net.treywood.actor.GraphQLSubscription.{NewSubscription, Update}
import net.treywood.http.Main
import sangria.schema.{Action, Value}

object GraphQLSubscription {
  case class NewSubscription(ref: ActorRef)
  case class Update(value: Any)
}

private[actor] class SubscriptionActor extends Actor {
  def waiting: Receive = {
    case NewSubscription(ref) =>
      context become connected(ref)
  }

  def connected(ref: ActorRef): Receive = {
    case msg: Update => ref ! msg
  }

  def receive = waiting
}

trait GraphQLSubscription[A] {
  import GraphQLSubscription._

  protected var subs: Set[ActorRef] = Set.empty

  protected def broadcast(value: A): Unit = subs.foreach(_ ! Update(value))

  def newSubscription[Ctx]: Source[Action[Ctx, A], NotUsed] = {
    val actor = Main.system.actorOf(Props[SubscriptionActor])
    Source.actorRef[Update](10, OverflowStrategy.fail)
      .mapMaterializedValue(ref => {
        actor ! NewSubscription(ref)
        subs += actor
        NotUsed
      })
      .map({
        case Update(a) => Value[Ctx, A](a.asInstanceOf[A])
      })
  }

}
