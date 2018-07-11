import Dependencies._

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
  new Dockerfile {
    val dockerAppPath = "/app/"
    val mainClassString = (mainClass in Compile).value.get
    val classpath = (fullClasspath in Compile).value

    from("java")
    add(classpath.files, dockerAppPath)
    entryPoint("java", "-cp", s"$dockerAppPath:$dockerAppPath/*", s"$mainClassString")
  }
}

imageNames in docker := Seq(ImageName(
  repository = name.value.toLowerCase,
  tag = Some(version.value))
)
