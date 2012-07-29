name := "actors"

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.0-M6"

resolvers ++= Seq(
  "OSS Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases",
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases"
)

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-concurrent" % "7.0.0-M1" % "compile",
  "com.typesafe.akka" % "akka-actor" % "2.1-M1" % "test",
  "org.scala-lang" % "scala-actors" % "2.10.0-M6" % "test",
  "org.specs2" %% "specs2" % "1.11" % "test",
  "junit" % "junit-dep" % "4.10" % "test"
)

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-explaintypes") 

parallelExecution in Test := false

fork in Test := true

javaOptions in Test += "-server -Xms4g -Xmx4g -Xss1m -XX:NewSize=3g -XX:PermSize=128m -XX:MaxPermSize=128m -XX:+TieredCompilation -XX:+UseParNewGC -XX:+UseNUMA -XX:+UseCondCardMark -XX:-UseBiasedLocking -XX:+AlwaysPreTouch"