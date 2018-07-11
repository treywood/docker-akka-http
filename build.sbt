import Dependencies._

lazy val root = (project in file("."))
  .enablePlugins(DockerPlugin, DockerComposePlugin)
  .settings(
    inThisBuild(List(
      organization := "net.treywood",
      scalaVersion := "2.12.5",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "docker-akka-http",
    libraryDependencies := dependencies,
    dockerImageCreationTask := docker.value
  )
