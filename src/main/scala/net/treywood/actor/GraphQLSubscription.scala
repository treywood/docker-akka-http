package net.treywood.actor

import akka.NotUsed
import akka.stream.scaladsl.Source
import org.reactivestreams.{Publisher, Subscriber}
import sangria.schema.{Action, Value}

trait GraphQLSubscription[A] {

  protected var subs: Set[Subscriber[_ >: A]] = Set.empty

  protected def broadcast(value: A): Unit = {
    println(s"broadcasting to ${subs.size} listeners. value $value")
    subs.foreach(_.onNext(value))
  }

  private lazy val publisher = new Publisher[A] {
    override def subscribe(s: Subscriber[_ >: A]) {
      println("new subscription")
      subs += s
    }
  }

  def source[Ctx]: Source[Action[Ctx,A], NotUsed] =
    Source.fromPublisher(publisher).map(v => {
      println(s"new value $v")
      Value[Ctx,A](v)
    })

}
