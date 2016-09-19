name := "akka-esper-integration"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.10",
  "com.espertech" % "esper" % "5.5.0",
  "com.gensler" % "scalavro-util_2.10" % "0.6.2",
  "com.typesafe.akka" %% "akka-stream" % "2.4.10"
)
