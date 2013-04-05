name := "actors"

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.1"

resolvers ++= Seq(
  "OSS Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases",
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases",
  "Java Affinity Binding Googlecode Snapshots" at "http://java-affinity-binding.googlecode.com/svn/repo"
)

libraryDependencies ++= Seq(
  "org.scalaz" % "scalaz-concurrent_2.10" % "7.0.0-RC1" % "compile",
  "com.api-tech" % "proxyactors_2.10" % "0.2.1" % "test",
  "com.typesafe.akka" % "akka-actor_2.10" % "2.2-M2" % "test",
  "org.scala-lang" % "scala-actors" % "2.10.1" % "test",
  "net.liftweb" % "lift-actor_2.10" % "2.5-RC3" % "test",
  "org.affinity" % "affinity" % "0.1.1-SNAPSHOT" % "test",
  "org.specs2" % "specs2_2.10" % "1.14" % "test",
  "junit" % "junit-dep" % "4.11" % "test"
)

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

