scalaVersion := "2.10.5"

organization := "guru.data-fellas"

name := "spark-notebook-core"

version := "0.2.0-SNAPSHOT"

bintrayRepository := "spark-notebook"

bintrayOrganization := Some("data-fellas")

bintrayVcsUrl := Some("https://github.com/spark-notebook/spark-notebook-core.git")

bintrayOmitLicense := true

publishArtifact in Test := false

publishMavenStyle := true

libraryDependencies +=  "com.typesafe.play" %% "play-json" % "2.3.10" excludeAll(
                          ExclusionRule("com.typesafe.akka"),
                          ExclusionRule("com.google.guava")
                        )

libraryDependencies += "org.apache.commons" % "commons-io" % "1.3.2"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "test"
