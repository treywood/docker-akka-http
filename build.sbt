import Dependencies._
import sbt.Keys._
import sbt._

name := "docker-akka-http"
version := "0.0.1"

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

  val skipUiCompile =
    System.getenv("ONESOURCE_UI_DEV") == "true" ||
      System.getenv("ONESOURCE_SKIP_UI_COMPILE") == "true"

  new Dockerfile {
    val dockerAppPath = "/app/"
    val mainClassString = (mainClass in Compile).value.get
    val classpath = (fullClasspath in Compile).value

    from("java")
    from("node:8")
    add(classpath.files, dockerAppPath)

    env("SRC_PATH", "./src")

    copy(baseDirectory(_ / "package.json").value, "./")
    copy(baseDirectory(_ / "yarn.lock").value, "./")
    copy(baseDirectory(_ / "webpack.config.js").value, "./")
    copy(baseDirectory(_ / "webpack.prod.js").value, "./")

    run("mkdir", "src")
    copy(dirFiles(baseDirectory(_ / "src" / "main" / "webapp").value), "/src/")
    run("ls", "-l", "src")

    run("yarn", "--pure-lockfile")
    run("yarn", "build")

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
