name := "akka-esper-integration"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.10",
  "com.espertech" % "esper" % "4.11.0",
  "com.gensler" % "scalavro-util_2.10" % "0.6.2",
  //"com.typesafe.akka" %% "akka-stream-experimental" % "0.2"
  "com.typesafe.akka" % "akka-stream_2.11" % "2.4.10"
)
