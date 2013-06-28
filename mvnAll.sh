#!/bin/bash
mvn -B clean test-compile >outX.txt
mvn -B -Dbenchmark.executorServiceType=fixed-thread-pool test >>outX.txt
mvn -B -Dbenchmark.executorServiceType=scala-forkjoin-pool test >>outX.txt
mvn -B -Dbenchmark.executorServiceType=java-forkjoin-pool test >>outX.txt
mvn -B -Dbenchmark.executorServiceType=thread-pool test >>outX.txt
