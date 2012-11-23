name := "actors"

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.0-RC2"

resolvers ++= Seq(
  "OSS Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases",
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases"
)

libraryDependencies ++= Seq(
  "org.scalaz" % "scalaz-concurrent_2.10.0-RC2" % "7.0.0-M4" % "compile",
  "com.typesafe.akka" % "akka-actor_2.10.0-RC2" % "2.1.0-RC2" % "test",
  "org.scala-lang" % "scala-actors" % "2.10.0-RC2" % "test",
  "org.specs2" % "specs2_2.10.0-RC2" % "1.12.2" % "test",
  "junit" % "junit-dep" % "4.11" % "test"
)

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-explaintypes") 

parallelExecution in Test := false

fork in Test := true

javaOptions in Test += "-server -Xms4g -Xmx4g -Xss1m -XX:NewSize=3g -XX:PermSize=128m -XX:MaxPermSize=128m -XX:+TieredCompilation -XX:+UseParNewGC -XX:+UseNUMA -XX:+UseCondCardMark -XX:-UseBiasedLocking -XX:+AlwaysPreTouch"