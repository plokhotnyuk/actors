#!/bin/bash
sbt -no-colors clean test:compile &>outX_poolSize100.txt
sbt -no-colors -Dbenchmark.poolSize=100 -Dbenchmark.executorServiceType=akka-forkjoin-pool test &>>outX_poolSize100.txt
sbt -no-colors -Dbenchmark.poolSize=100 -Dbenchmark.executorServiceType=java-forkjoin-pool test &>>outX_poolSize100.txt
sbt -no-colors -Dbenchmark.poolSize=100 -Dbenchmark.executorServiceType=abq-thread-pool test &>>outX_poolSize100.txt
sbt -no-colors -Dbenchmark.poolSize=100 -Dbenchmark.executorServiceType=lbq-thread-pool test &>>outX_poolSize100.txt
