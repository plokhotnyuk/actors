@echo off
call sbt clean test:compile exit >outX.txt
call sbt -Dbenchmark.executorServiceType=scala-forkjoin-pool test >>outX.txt
call sbt -Dbenchmark.executorServiceType=java-forkjoin-pool test >>outX.txt
call sbt -Dbenchmark.executorServiceType=thread-pool test >>outX.txt
