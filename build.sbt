name := "actors"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.4" % "test",
  "net.liftweb" %% "lift-actor" % "2.6-M4" % "test",
  "org.scala-lang" % "scala-actors" % "2.11.2" % "test",
  "org.scalaz" %% "scalaz-concurrent" % "7.1.0-RC1" % "test",
  "org.specs2" %% "specs2" % "2.3.13-scalaz-7.1.0-RC1" % "test",
  "junit" % "junit" % "4.11" % "test"
)

scalacOptions ++= Seq("-target:jvm-1.7", "-optimize", "-deprecation", "-unchecked")

parallelExecution in test := false

testGrouping in Test <<= definedTests in Test map { tests =>
  tests.map { test =>
    import Tests._
    import scala.collection.JavaConversions._
    new Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = SubProcess(javaOptions = Seq(
        "-server", "-Xms4096m", "-Xms4096m", "-XX:NewSize=3584m", "-Xss256k", "-XX:+TieredCompilation",
        "-XX:+UseParNewGC", "-XX:+UseConcMarkSweepGC", "-XX:CMSInitiatingOccupancyFraction=75", "-XX:+UseCMSInitiatingOccupancyOnly",
        "-XX:+UseNUMA", "-XX:+UseCondCardMark", "-XX:-UseBiasedLocking", "-XX:+AlwaysPreTouch") ++
        System.getProperties.toMap.map {
          case (k, v)  => "-D" + k + "=" + v
        }))
  }.sortWith(_.name < _.name)
}
