name := "actors"

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.0"

resolvers ++= Seq(
  "OSS Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases",
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases"
)

libraryDependencies ++= Seq(
  "org.scalaz" % "scalaz-concurrent_2.10" % "7.0.0-M7" % "compile",
  "com.typesafe.akka" % "akka-actor_2.10" % "2.1.0" % "test",
  "net.liftweb" % "lift-actor_2.10" % "2.5-M4" % "test",
  "org.scala-lang" % "scala-actors" % "2.10.0" % "test",
  "org.specs2" % "specs2_2.10" % "1.13" % "test",
  "junit" % "junit-dep" % "4.11" % "test"
)

parallelExecution in test := false

fork in test := true

javaOptions in test ++= Seq("-server", "-Xms4g", "-Xmx4g", "-Xss1m", "-XX:NewSize=3g", "-XX:PermSize=128m", "-XX:MaxPermSize=128m", "-XX:+TieredCompilation", 
  "-XX:+UseParallelGC", "-XX:+UseNUMA", "-XX:+UseCondCardMark", "-XX:-UseBiasedLocking", "-XX:+AlwaysPreTouch")

