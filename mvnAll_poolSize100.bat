@echo off
call mvn -B clean test-compile >outX_poolSize100.txt
call mvn -B -Dbenchmark.poolSize=100 -Dbenchmark.executorServiceType=akka-forkjoin-pool test >>outX_poolSize100.txt
call mvn -B -Dbenchmark.poolSize=100 -Dbenchmark.executorServiceType=scala-forkjoin-pool test >>outX_poolSize100.txt
call mvn -B -Dbenchmark.poolSize=100 -Dbenchmark.executorServiceType=java-forkjoin-pool test >>outX_poolSize100.txt
call mvn -B -Dbenchmark.poolSize=100 -Dbenchmark.executorServiceType=thread-pool test >>outX_poolSize100.txt
