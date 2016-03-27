name := "actors"
version := "1.0-SNAPSHOT"
scalaVersion := "2.11.8"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.2" % "test",
  "net.liftweb" %% "lift-actor" % "3.0-M8" % "test",
  "org.scala-lang" % "scala-actors" % scalaVersion.value % "test",
  "org.scalaz" %% "scalaz-concurrent" % "7.1.7" % "test",
  "org.specs2" %% "specs2-junit" % "2.5-scalaz-7.1.6" % "test",
  "org.hdrhistogram" % "HdrHistogram" % "2.1.8" % "test"
)
scalacOptions ++= Seq(s"-target:jvm-1.8", "-Ybackend:GenBCode", "-Ydelambdafy:method",
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
        "-XX:+TieredCompilation", "-XX:+ParallelRefProcEnabled ", "-XX:-UseBiasedLocking",
        "-XX:+AlwaysPreTouch", "-XX:+UnlockDiagnosticVMOptions", "-XX:GuaranteedSafepointInterval=30000") ++
        sys.props.map { case (k, v) => s"-D$k=$v" }))
  }
}