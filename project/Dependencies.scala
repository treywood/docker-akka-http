import sbt._

object Dependencies {
  val dependencies = Seq(
    "com.typesafe.akka" %% "akka-http"   % "10.1.3",
    "com.typesafe.akka" %% "akka-stream" % "2.5.12",
    "org.scalatest" %% "scalatest" % "3.0.5" % Test
  )
}
