#!/bin/bash
sbt clean test:compile >outX.txt
sbt -Dbenchmark.executorServiceType=akka-forkjoin-pool test >>outX.txt
sbt -Dbenchmark.executorServiceType=scala-forkjoin-pool test >>outX.txt
sbt -Dbenchmark.executorServiceType=java-forkjoin-pool test >>outX.txt
sbt -Dbenchmark.executorServiceType=abq-thread-pool test >>outX.txt
sbt -Dbenchmark.executorServiceType=lbq-thread-pool test >>outX.txt
sbt -Dbenchmark.executorServiceType=ltq-thread-pool test >>outX.txt
