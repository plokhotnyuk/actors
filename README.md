[![Build Status](https://secure.travis-ci.org/plokhotnyuk/actors.png)](http://travis-ci.org/plokhotnyuk/actors)

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
[Scalaz](https://github.com/scalaz/scalaz/blob/master/core/src/main/scala/scalaz/concurrent/Actor.scala) vs.
[alternative implementation of Scalaz actor](https://github.com/plokhotnyuk/actors/blob/master/src/main/scala/com/github/plokhotnyuk/actors/Actor2.scala)

## Hardware required

- CPU: 2 cores or more
- RAM: min 6Gb (for JDK 64-bit) or min 3Gb (for JDK 32-bit)

## Software installed required

- JDK: 1.7.0_x or newer (can require of removing of some unsupported JVM options from test configuration)
- Maven: 3.0.4 (or sbt: 0.12.1)

## Building & running benchmarks

Use following command-line instructions:
```sh
mvn -B clean install >outX.txt
```

## Test result descriptions

#### out0.txt
Intel(R) Core(TM) i7-2640M CPU @ 2.80GHz (max 3.50GHz), RAM 12Gb DDR3-1333, Windows 7 sp1, JDK 1.8.0-ea-b78 64-bit

#### out1.txt
Intel(R) Core(TM) i7-2640M CPU @ 2.80GHz (max 3.50GHz), RAM 12Gb DDR3-1333, Windows 7 sp1, JDK 1.7.0_15-b03 64-bit

#### out2.txt (with 2Gb of max heap size)
Intel(R) Core(TM)2 Duo CPU E6850 @ 3.00GHz, RAM 4Gb DDR2-800, Windows 7 sp1, JDK 1.7.0_10-b18 64-bit
(WARNING: Outdated test results)

#### out3.txt
Intel(R) Core(TM) i5-3570 CPU @ 3.40GHz (max 3.80GHz), RAM 16Gb DDR3-1333, Ubuntu 12.04, JDK 1.7.0_04-b20 64-bit

#### out4.txt
Intel(R) Core(TM) i7-3610QM CPU @ 2.30GHz (max 3.30GHz), RAM 8Gb DDR3-1333, Windows 8, JDK 1.7.0_11-b21 64-bit
(WARNING: Outdated test results)

#### out5.txt
Intel(R) Core(TM) i7-3615QM CPU @ 2.30GHz (max 3.30GHz), RAM 16Gb DDR3-1600, Mac OS X 10.8.2, JDK 1.7.0_07-b10 64-bit
(WARNING: Outdated test results)