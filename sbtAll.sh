#!/bin/bash
sbt clean test:compile >outX.txt
sbt -Dbenchmark.executorServiceType=scala-forkjoin-pool test >>outX.txt
sbt -Dbenchmark.executorServiceType=java-forkjoin-pool test >>outX.txt
sbt -Dbenchmark.executorServiceType=fast-thread-pool test >>outX.txt
sbt -Dbenchmark.executorServiceType=thread-pool test >>outX.txt