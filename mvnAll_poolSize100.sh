#!/bin/bash
mvn -B clean test-compile >outX_poolSize100.txt
mvn -B -Dbenchmark.poolSize=100 -Dbenchmark.executorServiceType=scala-forkjoin-pool test >>outX_poolSize100.txt
mvn -B -Dbenchmark.poolSize=100 -Dbenchmark.executorServiceType=java-forkjoin-pool test >>outX_poolSize100.txt
mvn -B -Dbenchmark.poolSize=100 -Dbenchmark.executorServiceType=jsr166e-forkjoin-pool test >>outX_poolSize100.txt
mvn -B -Dbenchmark.poolSize=100 -Dbenchmark.executorServiceType=thread-pool test >>outX_poolSize100.txt
