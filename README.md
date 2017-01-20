```sh
  ☆ノノハ
  从*’w’)
(つactorsと)
```

Evaluation of API and performance of in-memory messaging for different actor implementations written on Scala:
[Akka](https://github.com/akka/akka/blob/master/akka-actor/src/main/scala/akka/actor/Actor.scala) vs.
[Lift](https://github.com/lift/framework/blob/master/core/actor/src/main/scala/net/liftweb/actor/LiftActor.scala) vs.
[Scala](https://github.com/scala/scala/blob/master/src/actors/scala/actors/Actor.scala) vs.
[Scalaz](https://github.com/scalaz/scalaz/blob/master/core/src/main/scala/scalaz/concurrent/Actor.scala)

This project provides and tests alternative implementations of Minimalist actor and bounded/unbounded mailboxes for Akka:
[Akka](https://github.com/plokhotnyuk/actors/blob/master/src/test/scala/akka/dispatch/Mailboxes.scala) vs.
[Minimalist Scala Actor](https://github.com/plokhotnyuk/actors/blob/master/src/test/scala/com/github/gist/viktorklang/Actor.scala)
also it provides alternative fork-join tasks which increase efficiency of actors and examples of their usage
with Lift, Scala & Scalaz actors.

[![Travis CI Build Status](https://secure.travis-ci.org/plokhotnyuk/actors.png)](http://travis-ci.org/plokhotnyuk/actors)

[![Wercker Build Status](https://app.wercker.com/status/25f11d8f54baf3a84d8ae429465bcbb3/s "wercker status")](https://app.wercker.com/project/bykey/25f11d8f54baf3a84d8ae429465bcbb3)

[![Circle CI Build Status](https://circleci.com/gh/plokhotnyuk/actors.svg?style=shield)](https://circleci.com/gh/plokhotnyuk/actors)

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/plokhotnyuk/actors?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## Benchmarks and their goals

* `Enqueueing` - memory footprint of internal actor queue per submitted message and submition throughput in a single thread
* `Dequeueing` - message handling throughput in a single thread
* `Initiation` - memory footprint of minimal actors and initiation time in a single thread
* `Single-producer sending` - throughput of message sending from external thread to an actor
* `Multi-producer sending` - throughput of message sending from several external threads to an actor
* `Max throughput` - throughput of message sending from several external threads to several actors
* `Ping latency` - average latency of sending of a ping message between two actors
* `Ping throughput 10K` - throughput of executor service by sending of a ping message between 10K pairs of actors
* `Overflow throughput` - throughput of overflow handing for bounded version of actors

## Hardware required
- CPU: 2 cores or more
- RAM: 6Gb or greater

## Software installed required
- JDK: 1.8.0_x
- Maven: 3.x or sbt: 0.13.x

## Building & running benchmarks
Before benchmark running check if your CPU works in most performant mode (not a powersave one). Check it on Linux by following command:
`cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor`

Use following command-line instructions to build from sources and run benchmarks with Scala's ForkJoinPool in FIFO mode:
```sh
mvn -B clean test >outX.txt
```
or
```sh
sbt clean test >outX.txt
```

Use `mvnAll.sh` or `sbtAll.sh` scripts (for Windows: `mvnAll.bat` or `sbtAll.bat`) to run benchmarks for the following types of executor services:
- `akka-forkjoin-pool` for `akka.dispatch.ForkJoinExecutorConfigurator.AkkaForkJoinPool`
- `scala-forkjoin-pool` for `scala.concurrent.forkjoin.ForkJoinPool`
- `java-forkjoin-pool` for `java.util.concurrent.ForkJoinPool`
- `abq-thread-pool` for `java.util.concurrent.ThreadPoolExecutor` with `java.util.concurrent.ArrayBlockingQueue`
- `lbq-thread-pool` for `java.util.concurrent.ThreadPoolExecutor` with `java.util.concurrent.LinkedBlockingQueue`

Recommended values of JVM options which can be set for MAVEN_OPTS and SBT_OPTS system variables:

```sh
-server -Xms1g -Xmx1g -Xss1m -XX:NewSize=512m -XX:+TieredCompilation -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:-UseBiasedLocking -XX:+AlwaysPreTouch
```

## Known issues
1. Benchmark freeze with Java ForkJoinPool baked by 1 thread on 8u40, 8u45, 8u51 and some early 8u60 ea builds, please see details here: 
https://bugs.openjdk.java.net/browse/JDK-8078490

W/A is to upgrade to latest Java 8 build (8u60-b27 or above) or to use latest jsr166.jar (link to download http://gee.cs.oswego.edu/dl/jsr166/dist/jsr166.jar) 
in working directory with following JVM option to pick it up: `-Xbootclasspath/p:jsr166.jar`

## Test result descriptions
Results of running mvnAll.bat or mvnAll.sh scripts on different environments with pool size (or number of worker threads)
set to number of available processors, 1, 10 or 100 values accordingly:

#### out0*.txt
Intel(R) Core(TM) i7-2640M CPU @ 2.80GHz (max 3.50GHz), RAM 12Gb DDR3-1333, Ubuntu 14.04.1, Linux 4.4.0-38-generic, Oracle JDK build 1.8.0_112-b15 64-bit
