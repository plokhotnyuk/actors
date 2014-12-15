@echo off
call mvn -B clean test-compile >outX_poolSize10.txt
call mvn -B -Dbenchmark.poolSize=10 -Dbenchmark.executorServiceType=akka-forkjoin-pool test >>outX_poolSize10.txt
call mvn -B -Dbenchmark.poolSize=10 -Dbenchmark.executorServiceType=scala-forkjoin-pool test >>outX_poolSize10.txt
call mvn -B -Dbenchmark.poolSize=10 -Dbenchmark.executorServiceType=java-forkjoin-pool test >>outX_poolSize10.txt
call mvn -B -Dbenchmark.poolSize=10 -Dbenchmark.executorServiceType=abq-thread-pool test >>outX_poolSize10.txt
call mvn -B -Dbenchmark.poolSize=10 -Dbenchmark.executorServiceType=lbq-thread-pool test >>outX_poolSize10.txt
