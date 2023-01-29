 version := "0.1.0-SNAPSHOT"

 scalaVersion := "2.12.12"

lazy val root = (project in file("."))
  .settings(
    name := "assignment1"
  )

 libraryDependencies ++= Seq(
   "com.typesafe.akka" %% "akka-http" % "10.1.12",
   "com.typesafe.akka" %% "akka-stream" % "2.6.10",
   "com.typesafe.akka" %% "akka-actor" % "2.6.10",
   "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.12",
   "io.spray" %% "spray-json" % "1.3.5",
   "org.hibernate" % "hibernate-core" % "5.4.22.Final",
   "org.postgresql" % "postgresql" % "42.2.18",
   "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.1",
   "org.slf4j" % "slf4j-api" % "1.7.5",
   "org.slf4j" % "slf4j-simple" % "1.7.5"
 )
