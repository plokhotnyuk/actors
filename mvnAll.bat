@echo off
call mvn -B clean test-compile >outX.txt
call mvn -B -Dbenchmark.executorServiceType=fixed-thread-pool test >>outX.txt
call mvn -B -Dbenchmark.executorServiceType=jsr166e-forkjoin-pool test >>outX.txt
call mvn -B -Dbenchmark.executorServiceType=scala-forkjoin-pool test >>outX.txt
call mvn -B -Dbenchmark.executorServiceType=java-forkjoin-pool test >>outX.txt
call mvn -B -Dbenchmark.executorServiceType=thread-pool test >>outX.txt
