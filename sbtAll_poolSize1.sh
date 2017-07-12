#!/bin/bash
sbt -no-colors clean test:compile &>outX_poolSize1.txt
sbt -no-colors -Dbenchmark.poolSize=1 -Dbenchmark.executorServiceType=akka-forkjoin-pool test &>>outX_poolSize1.txt
sbt -no-colors -Dbenchmark.poolSize=1 -Dbenchmark.executorServiceType=java-forkjoin-pool test &>>outX_poolSize1.txt
sbt -no-colors -Dbenchmark.poolSize=1 -Dbenchmark.executorServiceType=abq-thread-pool test &>>outX_poolSize1.txt
sbt -no-colors -Dbenchmark.poolSize=1 -Dbenchmark.executorServiceType=lbq-thread-pool test &>>outX_poolSize1.txt
