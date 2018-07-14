import Dependencies._
import sbt.Keys._
import sbt._

import scala.sys.process._

name := "docker-akka-http"
version := "0.0.2"

scalaVersion := "2.12.0"

libraryDependencies := dependencies

enablePlugins(DockerPlugin, DockerComposePlugin)

//Only execute tests tagged as the following
testTagsToExecute := "DockerComposeTag"

//Specify that an html report should be created for the test pass
testExecutionArgs := "-h target/htmldir"

//Set the image creation Task to be the one used by sbt-docker
dockerImageCreationTask := docker.value

dockerfile in docker := {

  "yarn build" !

  new Dockerfile {

    val dockerAppPath = "/app/"

    val mainClassString = (mainClass in Compile).value.get
    val classpath = (fullClasspath in Compile).value

    from("java")

    add(classpath.files, dockerAppPath)
    add(baseDirectory(_ / "target" / "app.tar.gz").value, "/app/webapp")

    entryPoint("java", "-cp", s"$dockerAppPath:$dockerAppPath/*", s"$mainClassString")
  }
}

imageNames in docker := Seq(
  ImageName(s"edwinjwood/${name.value}:latest")
)

def dirFiles(dir: File): Seq[File] = if (dir == null) Seq.empty[File] else {
  val files = dir.listFiles
  files ++ files.filter(f => f != null && f.isDirectory).flatMap(_.listFiles)
}
