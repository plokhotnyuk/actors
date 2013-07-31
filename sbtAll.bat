@echo off
call sbt clean test:compile exit >outX.txt
call sbt -Dbenchmark.parallelism=100 -Dbenchmark.executorServiceType=fixed-thread-pool test >>outX.txt
call sbt -Dbenchmark.parallelism=100 -Dbenchmark.executorServiceType=scala-forkjoin-pool test >>outX.txt
call sbt -Dbenchmark.parallelism=100 -Dbenchmark.executorServiceType=java-forkjoin-pool test >>outX.txt
call sbt -Dbenchmark.parallelism=100 -Dbenchmark.executorServiceType=thread-pool test >>outX.txt
