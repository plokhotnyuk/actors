```sh
  ☆ノノハ
  从*’w’)
(つactorsと)
```

Evaluation of API and performance of different actor libraries written on Scala:
[Akka](https://github.com/akka/akka/blob/master/akka-actor/src/main/scala/akka/actor/Actor.scala) vs.
[Lift](https://github.com/lift/framework/blob/master/core/actor/src/main/scala/net/liftweb/actor/LiftActor.scala) vs.
[ProxyActors](https://github.com/nu11ptr/ProxyActors/blob/master/src/main/scala/api/actor/package.scala) vs.
[Scala](https://github.com/scala/scala/blob/master/src/actors/scala/actors/Actor.scala) vs.
[Scalaz](https://github.com/scalaz/scalaz/blob/master/core/src/main/scala/scalaz/concurrent/Actor.scala)

[![Build Status](https://secure.travis-ci.org/plokhotnyuk/actors.png)](http://travis-ci.org/plokhotnyuk/actors)

## Hardware required

- CPU: 2 cores or more
- RAM: min 6Gb (for JDK 64-bit) or min 3Gb (for JDK 32-bit)

## Software installed required

- JDK: 1.7.0_x or newer (can require of removing of some unsupported JVM options from test configuration)
- Maven: 3.0.4 or sbt: 0.12.3

## Building & running benchmarks

Use following command-line instructions to build from sources and run benchmarks with Scala's ForkJoinPool in FIFO mode:
```sh
mvn -B clean test >outX.txt
```
or
```sh
sbt clean test >outX.txt
```

To run benchmarks for all available types of executor service use mvnAll.sh or sbtAll.sh scripts (for Windows: mvnAll.bat or sbtAll.bat).

Recommended values of JVM options which can be set for MAVEN_OPTS and SBT_OPTS system variables:

```sh
-server -Xms1g -Xmx1g -Xss1m -XX:NewSize=512m -XX:PermSize=256m -XX:MaxPermSize=256m -XX:+TieredCompilation -XX:+UseG1GC -XX:+UseNUMA -XX:+UseCondCardMark -XX:-UseBiasedLocking -XX:+AlwaysPreTouch
```

## Test result descriptions

Results of running mvnAll.bat or mvnAll.sh scripts on different enviromnents:

#### out0.txt
Intel(R) Core(TM) i7-2640M CPU @ 2.80GHz (max 3.50GHz), RAM 12Gb DDR3-1333, Windows 7 sp1, Oracle JDK 1.8.0-ea-b93 64-bit

#### out1.txt and out1_poolSize100.txt <-- last with pool size set to 100
Intel(R) Core(TM) i7-2640M CPU @ 2.80GHz (max 3.50GHz), RAM 12Gb DDR3-1333, Windows 7 sp1, Oracle JDK 1.7.0_40-ea-b29 64-bit

#### out2.txt
Intel(R) Core(TM) i5-3570 CPU @ 3.40GHz (max 3.80GHz), RAM 16Gb DDR3-1333, Ubuntu 12.04, JDK 1.7.0_40-ea-b28 64-bit

#### out3_affinityOn.txt <-- 50M messages per second of throughput for Akka actors (with thread affinity on)
Intel(R) Core(TM) i7-3770 CPU @ 3.40GHz (max 3.90GHz) overclocked to 4.30GHz, RAM 32Gb DDR3-1666, Linux Mint 14, JDK 1.7.0_21-b11 64-bit

