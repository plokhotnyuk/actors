@echo off
call mvn -B clean test-compile >outX.txt
call mvn -B -Dbenchmark.poolSize=1000 -Dbenchmark.executorServiceType=scala-forkjoin-pool test >>outX.txt
call mvn -B -Dbenchmark.poolSize=1000 -Dbenchmark.executorServiceType=java-forkjoin-pool test >>outX.txt
call mvn -B -Dbenchmark.poolSize=1000 -Dbenchmark.executorServiceType=fixed-thread-pool test >>outX.txt
call mvn -B -Dbenchmark.poolSize=1000 -Dbenchmark.executorServiceType=thread-pool test >>outX.txt