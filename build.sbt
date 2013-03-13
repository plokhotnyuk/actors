name := "actors"

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.1"

resolvers ++= Seq(
  "OSS Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases",
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases"
)

libraryDependencies ++= Seq(
  "org.scalaz" % "scalaz-concurrent_2.10" % "7.0.0-M8" % "compile",
  "com.api-tech" % "proxyactors_2.10" % "0.2.1" % "test",
  "com.typesafe.akka" % "akka-actor_2.10" % "2.2-M1" % "test",
  "net.liftweb" % "lift-actor_2.10" % "2.5-RC1" % "test",
  "org.scala-lang" % "scala-actors" % "2.10.1" % "test",
  "org.specs2" % "specs2_2.10" % "1.14" % "test",
  "junit" % "junit-dep" % "4.11" % "test"
)

parallelExecution in test := false

fork in test := true

javaOptions in test ++= Seq("-server", "-Xms4g", "-Xmx4g", "-Xss1m", "-XX:NewSize=3g", "-XX:PermSize=128m", "-XX:MaxPermSize=128m", "-XX:+TieredCompilation", 
  "-XX:+UseG1GC", "-XX:+UseNUMA", "-XX:+UseCondCardMark", "-XX:-UseBiasedLocking", "-XX:+AlwaysPreTouch")

