name := "actors"

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.3-RC1"

resolvers ++= Seq(
  "OSS Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases",
  "OSS Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases",
  "Typesafe Snapshots" at "http://repo.typesafe.com/typesafe/snapshots"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.2.1" % "test",
  "net.liftweb" %% "lift-actor" % "2.5.1" % "test",
  "com.api-tech" %% "proxyactors" % "0.2.1" % "test",
  "org.scala-lang" % "scala-actors" % "2.10.2" % "test",
  "org.scalaz" %% "scalaz-concurrent" % "7.1.0-M2" % "test",
  "com.higherfrequencytrading" % "affinity" % "1.7" % "test",
  "org.scalatest" %% "scalatest" % "2.0.M8" % "test",
  "junit" % "junit-dep" % "4.11" % "test"
)

scalacOptions ++= Seq("-deprecation", "-unchecked")

parallelExecution in test := false

testGrouping <<= definedTests in Test map { tests =>
  tests.map { test =>
    import Tests._
    import scala.collection.JavaConversions._
    new Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = SubProcess(javaOptions = Seq("-server", "-Xms8g", "-Xmx8g", "-Xss1m", "-XX:NewSize=7g", "-XX:PermSize=128m", "-XX:MaxPermSize=128m",
        "-XX:+TieredCompilation", "-XX:+UseG1GC", "-XX:+UseNUMA", "-XX:+UseCondCardMark", "-XX:-UseBiasedLocking", "-XX:+AlwaysPreTouch") ++
        System.getProperties.propertyNames.toSeq.map(key => "-D" + key.toString + "=" + System.getProperty(key.toString))))
  }.filter(!_.name.contains("ProxyActors")).sortWith(_.name < _.name)
}
