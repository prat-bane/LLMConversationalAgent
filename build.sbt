ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

val AkkaVersion = "2.7.0"
val AkkaHttpVersion = "10.5.0"
val GrpcVersion = "1.42.1"

lazy val root = (project in file("."))
 // .enablePlugins(AkkaGrpcPlugin) //
  .settings(
    name := "LLMConversationalAgent"
  )


libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "com.typesafe" % "config" % "1.4.2",

  // Logging dependencies
  "ch.qos.logback" % "logback-classic" % "1.4.7",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "org.slf4j" % "slf4j-api" % "2.0.7",

  //gRPC
  // gRPC dependencies
  "io.grpc" % "grpc-netty-shaded" % scalapb.compiler.Version.grpcJavaVersion,
  "io.grpc" % "grpc-netty" % GrpcVersion,
  "io.grpc" % "grpc-protobuf" % GrpcVersion,
  "io.grpc" % "grpc-stub" % GrpcVersion,
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,

  //bedrock
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.2",
  "com.amazonaws" % "aws-lambda-java-events" % "3.11.1",
  "software.amazon.awssdk" % "bedrockruntime" % "2.21.45",
  "software.amazon.awssdk" % "apache-client" % "2.21.45",
  "org.apache.httpcomponents" % "httpclient" % "4.5.13",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.14.2",
  "org.scala-lang.modules" %% "scala-collection-compat" % "2.10.0",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.14.2",

  // Testing
  "org.scalatest" %% "scalatest" % "3.2.15" % Test,
  "org.scalatestplus" %% "mockito-4-6" % "3.2.15.0" % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test
)

assembly / assemblyMergeStrategy := {
  case "reference.conf" => MergeStrategy.concat
  case "application.conf" => MergeStrategy.concat
  case "logback.xml" => MergeStrategy.first
  case PathList("META-INF", xs @ _*) => xs match {
    case "MANIFEST.MF" :: Nil => MergeStrategy.discard
    case _ => MergeStrategy.first
  }
  case x => MergeStrategy.first
}


Compile / PB.targets := Seq(
  // Generate Scala code with gRPC support
  scalapb.gen(grpc = true) -> (Compile / sourceManaged).value
)