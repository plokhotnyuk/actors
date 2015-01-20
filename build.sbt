name := "actors"
version := "1.0-SNAPSHOT"
scalaVersion := "2.11.5"
resolvers ++= Seq("sonatype-staging" at "https://oss.sonatype.org/content/groups/staging")
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.9" % "test",
  "net.liftweb" %% "lift-actor" % "3.0-M3" % "test",
  "org.scala-lang" % "scala-actors" % "2.11.5" % "test",
  "org.scalaz" %% "scalaz-concurrent" % "7.1.0" % "test",
  "org.specs2" %% "specs2-junit" % "2.4.15" % "test"
)
scalacOptions ++= Seq("-target:jvm-1.7", "-optimize", "-deprecation", "-unchecked", "-feature",
  "-language:implicitConversions", "-Xlog-reflective-calls", "-Xfuture", "-Xlint")
parallelExecution in test := false
testGrouping in Test <<= definedTests in Test map { tests =>
  tests.map { test =>
    import Tests._
    import scala.collection.JavaConversions._
    new Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = SubProcess(javaOptions = Seq(
        "-server", "-Xms4096m", "-Xms4096m", "-XX:NewSize=3584m", "-Xss256k", "-XX:+TieredCompilation", "-XX:+UseG1GC", 
        "-XX:+UseNUMA", "-XX:-UseBiasedLocking", "-XX:+AlwaysPreTouch") ++
        System.getProperties.toMap.map {
          case (k, v)  => "-D" + k + "=" + v
        }))
  }.sortWith(_.name < _.name)
}
