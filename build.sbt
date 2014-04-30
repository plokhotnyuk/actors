name := "actors"

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.2" % "test",
  "net.liftweb" %% "lift-actor" % "2.6-M3" % "test",
  "org.scala-lang" % "scala-actors" % "2.10.4" % "test",
  "org.scalaz" %% "scalaz-concurrent" % "7.1.0-M6" % "test",
  "com.twitter" % "jsr166e" % "1.1.0" % "test",
  "org.specs2" %% "specs2" % "2.3.10-scalaz-7.1.0-M6" % "test",
  "junit" % "junit-dep" % "4.11" % "test"
)

scalacOptions ++= Seq("-target:jvm-1.7", "-optimize", "-deprecation", "-unchecked")

parallelExecution in test := false

testGrouping <<= definedTests in Test map { tests =>
  tests.map { test =>
    import Tests._
    import scala.collection.JavaConversions._
    new Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = SubProcess(javaOptions = Seq(
        "-server", "-Xms4096m", "-Xms4096m", "-XX:NewSize=3584m", "-Xss228k", "-XX:+UseG1GC", "-XX:GCTimeRatio=90",
        "-XX:+TieredCompilation", "-XX:+UseNUMA", "-XX:+UseCondCardMark", "-XX:-UseBiasedLocking", "-XX:+AlwaysPreTouch") ++
        System.getProperties.propertyNames.toSeq.map(key => "-D" + key.toString + "=" + System.getProperty(key.toString))))
  }.sortWith(_.name < _.name)
}
