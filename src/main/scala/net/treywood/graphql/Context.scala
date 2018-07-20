package net.treywood.graphql

import akka.actor.Props
import net.treywood.actor.ToDoActor
import net.treywood.http.Main

class Context

object Context {
  lazy val toDoActor = Main.system.actorOf(Props[ToDoActor])
}
