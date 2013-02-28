[![Build Status](https://secure.travis-ci.org/plokhotnyuk/actors.png)](http://travis-ci.org/plokhotnyuk/actors)

```sh
  ☆ノノハ
  从*’w’)
(つactorsと)
```

Evaluation of API and performance of different actor libraries

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
(Outdated) Intel(R) Core(TM)2 Duo CPU E6850 @ 3.00GHz, RAM 4Gb DDR2-800, Windows 7 sp1, JDK 1.7.0_10-b18 64-bit

#### out3.txt
(Outdated) Intel(R) Core(TM) i5-3570 CPU @ 3.40GHz (max 3.80GHz), RAM 16Gb DDR3-1333, Ubuntu 12.04, JDK 1.7.0_04-b20 64-bit

#### out4.txt
(Outdated) Intel(R) Core(TM) i7-3610QM CPU @ 2.30GHz (max 3.30GHz), RAM 8Gb DDR3-1333, Windows 8, JDK 1.7.0_11-b21 64-bit

#### out5.txt
(Outdated) Intel(R) Core(TM) i7-3615QM CPU @ 2.30GHz (max 3.30GHz), RAM 16Gb DDR3-1600, Mac OS X 10.8.2, JDK 1.7.0_07-b10 64-bit