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

This project provide and tests some alternative implementations of Scalaz and Minimalist actors and mailboxes for Akka:
[Akka](https://github.com/plokhotnyuk/actors/blob/master/src/test/scala/akka/dispatch/Mailboxes.scala) vs.
[Minimalist Scala Actor](https://gist.github.com/viktorklang/2362563) vs.
[Scalaz](https://github.com/plokhotnyuk/actors/blob/master/src/test/scala/scalaz/concurrent/Actor2.scala)

[![Build Status](https://secure.travis-ci.org/plokhotnyuk/actors.png)](http://travis-ci.org/plokhotnyuk/actors)

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
- JDK: 1.7.0_x or newer
- Maven: 3.x or sbt: 0.13.x

## Building & running benchmarks
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
-server -Xms1g -Xmx1g -Xss1m -XX:NewSize=512m -XX:PermSize=256m -XX:MaxPermSize=256m -XX:+TieredCompilation -XX:+UseG1GC -XX:+UseNUMA -XX:-UseBiasedLocking -XX:+AlwaysPreTouch
```

## Test result descriptions
Results of running mvnAll.bat or mvnAll.sh scripts on different environments with pool size (or number of worker threads)
set to number of available processors, 1, 10 or 100 values accordingly:

#### out0*.txt
Intel(R) Core(TM) i7-2640M CPU @ 2.80GHz (max 3.50GHz), RAM 12Gb DDR3-1333, Windows 7 sp1, Oracle JDK 1.8.0_25-b18 64-bit

#### out1*.txt
Intel(R) Core(TM) i7-2640M CPU @ 2.80GHz (max 3.50GHz), RAM 12Gb DDR3-1333, Windows 7 sp1, Oracle JDK 1.7.0_72-b14 64-bit

#### out2*.txt
Intel(R) Core(TM) i7-4850MQ CPU @ 2.30GHz (max 3.50GHz), RAM 16Gb DDR3-1600, Mac OS X 10.10, Oracle JDK 1.8.0_25-b18 64-bit

#### out3*.txt
Intel(R) Core(TM) i7-4850MQ CPU @ 2.30GHz (max 3.50GHz), RAM 16Gb DDR3-1600, Mac OS X 10.10, Oracle JDK 1.7.0_72-b14 64-bit

