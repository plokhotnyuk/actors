@echo off
call mvn -B clean test-compile >outX.txt
call mvn -B -Dbenchmark.executorServiceType=injection-executor test >>outX.txt
call mvn -B -Dbenchmark.executorServiceType=akka-forkjoin-pool test >>outX.txt
call mvn -B -Dbenchmark.executorServiceType=scala-forkjoin-pool test >>outX.txt
call mvn -B -Dbenchmark.executorServiceType=java-forkjoin-pool test >>outX.txt
call mvn -B -Dbenchmark.executorServiceType=abq-thread-pool test >>outX.txt
call mvn -B -Dbenchmark.executorServiceType=lbq-thread-pool test >>outX.txt
