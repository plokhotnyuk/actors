name := "actors"
version := "1.0-SNAPSHOT"
scalaVersion := "2.11.6"
resolvers ++= Seq("sonatype-staging" at "https://oss.sonatype.org/content/groups/staging")
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.10" % "test",
  "net.liftweb" %% "lift-actor" % "3.0-M5" % "test",
  "org.scala-lang" % "scala-actors" % scalaVersion.value % "test",
  "org.scalaz" %% "scalaz-concurrent" % "7.2.0-M1" % "test",
  "org.specs2" %% "specs2-junit" % "2.4.17" % "test"
)
scalacOptions ++= Seq(s"-target:jvm-${sys.props("java.runtime.version").take(3)}",
  "-optimize", "-deprecation", "-unchecked", "-feature", "-language:implicitConversions",
  "-Xlog-reflective-calls", "-Xfuture", "-Xlint")
parallelExecution in test := false
testGrouping in Test <<= definedTests in Test map { tests =>
  tests.sortBy(_.name).map { test =>
    import Tests._
    new Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = SubProcess(javaOptions = Seq(
        "-server", "-Xms4096m", "-Xms4096m", "-XX:NewSize=3584m", "-XX:MaxNewSize=3584m", "-Xss256k", "-XX:+UseG1GC",
        "-XX:+TieredCompilation", "-XX:+UseNUMA", "-XX:-UseBiasedLocking", "-XX:+AlwaysPreTouch") ++
        sys.props.map { case (k, v) => s"-D$k=$v" }))
  }
}
