#!/bin/bash
mvn -B clean test-compile >outX_poolSize1.txt
mvn -B -Dbenchmark.poolSize=1 -Dbenchmark.executorServiceType=akka-forkjoin-pool test >>outX_poolSize1.txt
mvn -B -Dbenchmark.poolSize=1 -Dbenchmark.executorServiceType=scala-forkjoin-pool test >>outX_poolSize1.txt
mvn -B -Dbenchmark.poolSize=1 -Dbenchmark.executorServiceType=java-forkjoin-pool test >>outX_poolSize1.txt
mvn -B -Dbenchmark.poolSize=1 -Dbenchmark.executorServiceType=thread-pool test >>outX_poolSize1.txt
