name := "actors"

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.1"

resolvers ++= Seq(
  "OSS Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases",
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.2-M3" % "test",
  "net.liftweb" %% "lift-actor" % "2.5-RC5" % "test",
  "com.api-tech" %% "proxyactors" % "0.2.1" % "test",
  "org.scala-lang" % "scala-actors" % "2.10.1" % "test",
  "org.scalaz" %% "scalaz-concurrent" % "7.0.0" % "test",
  "com.higherfrequencytrading" % "affinity" % "1.7" % "test",
  "org.specs2" %% "specs2" % "1.14" % "test",
  "junit" % "junit-dep" % "4.11" % "test"
)

scalacOptions ++= Seq("-deprecation", "-unchecked")

parallelExecution in test := false

testGrouping <<= definedTests in Test map { tests =>
  tests.map { test =>
    import Tests._
    new Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = SubProcess(javaOptions = Seq("-server", "-Xms4g", "-Xmx4g", "-Xss1m", "-XX:NewSize=3g", "-XX:PermSize=128m", "-XX:MaxPermSize=128m",
        "-XX:+TieredCompilation", "-XX:+UseG1GC", "-XX:+UseNUMA", "-XX:+UseCondCardMark", "-XX:-UseBiasedLocking", "-XX:+AlwaysPreTouch")))
  }.sortWith(_.name < _.name)
}