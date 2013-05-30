#!/bin/bash
mvn -B -Dbenchmark.executorServiceType=fifo-forkjoin-pool clean test >outX.txt
mvn -B -Dbenchmark.executorServiceType=lifo-forkjoin-pool test >>outX.txt
mvn -B -Dbenchmark.executorServiceType=fast-thread-pool test >>outX.txt
mvn -B -Dbenchmark.executorServiceType=thread-pool test >>outX.txt