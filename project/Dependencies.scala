import sbt._

object Dependencies {
  val dependencies = Seq(
    "io.spray"            %%  "spray-json"          % "1.3.4",

    "com.typesafe.akka"   %% "akka-http-spray-json" % "10.1.3",
    "com.typesafe.akka"   %% "akka-http"            % "10.1.3",
    "com.typesafe.akka"   %% "akka-stream"          % "2.5.12",

    "org.sangria-graphql" %% "sangria"              % "1.4.1",

    "org.scalatest" %% "scalatest" % "3.0.5" % Test
  )
}
