#!/bin/bash
sbt -no-colors clean test:compile &>outX.txt
sbt -no-colors -Dbenchmark.executorServiceType=akka-forkjoin-pool test &>>outX.txt
sbt -no-colors -Dbenchmark.executorServiceType=java-forkjoin-pool test &>>outX.txt
sbt -no-colors -Dbenchmark.executorServiceType=abq-thread-pool test &>>outX.txt
sbt -no-colors -Dbenchmark.executorServiceType=lbq-thread-pool test &>>outX.txt
