name := "actors"
version := "1.0-SNAPSHOT"
scalaVersion := "2.12.2"
crossScalaVersions := Seq("2.11.11", "2.12.2")
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.3" % "test",
  "net.liftweb" %% "lift-actor" % "3.1.0" % "test",
  "org.scalaz" %% "scalaz-concurrent" % "7.3.0-M14" % "test",
  "org.scalatest" %% "scalatest" % "3.0.3" % "test",
  "org.hdrhistogram" % "HdrHistogram" % "2.1.9" % "test",
  "org.agrona" % "agrona" % "0.9.6" % "test"
)
scalacOptions ++= Seq(s"-target:jvm-1.8", "-Ydelambdafy:method",
  "-optimize", "-deprecation", "-unchecked", "-feature", "-language:implicitConversions",
  "-Xlog-reflective-calls", "-Xfuture", "-Xlint")
parallelExecution in test := false
testGrouping in Test <<= definedTests in Test map { tests =>
  tests.sortBy(_.name).map { test =>
    import Tests._
    Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = SubProcess(javaOptions = Seq(
        "-server", "-Xms4096m", "-Xms4096m", "-XX:NewSize=3584m", "-XX:MaxNewSize=3584m", "-Xss256k", "-XX:+UseG1GC",
        "-XX:+TieredCompilation", "-XX:+ParallelRefProcEnabled", "-XX:-UseBiasedLocking",
        "-XX:+AlwaysPreTouch", "-XX:+UnlockDiagnosticVMOptions", "-XX:GuaranteedSafepointInterval=30000") ++
        sys.props.map { case (k, v) => s"-D$k=$v" }))
  }
}