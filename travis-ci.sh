#!/bin/bash
mvn -B test-compile
mvn -B -Dbenchmark.executorServiceType=fixed-thread-pool test -Ptravis
mvn -B -Dbenchmark.executorServiceType=scala-forkjoin-pool test -Ptravis
mvn -B -Dbenchmark.executorServiceType=java-forkjoin-pool test -Ptravis
mvn -B -Dbenchmark.executorServiceType=thread-pool test -Ptravis
