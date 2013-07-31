#!/bin/bash
mvn -B clean test-compile >outX.txt
mvn -B -Dbenchmark.parallelism=100 -Dbenchmark.executorServiceType=fixed-thread-pool test >>outX.txt
mvn -B -Dbenchmark.parallelism=100 -Dbenchmark.executorServiceType=scala-forkjoin-pool test >>outX.txt
mvn -B -Dbenchmark.parallelism=100 -Dbenchmark.executorServiceType=java-forkjoin-pool test >>outX.txt
mvn -B -Dbenchmark.parallelism=100 -Dbenchmark.executorServiceType=thread-pool test >>outX.txt
