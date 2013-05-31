@echo off
call sbt clean test:compile exit >outX.txt
call sbt -Dbenchmark.executorServiceType=scala-forkjoin-pool test exit >>outX.txt
call sbt -Dbenchmark.executorServiceType=java-forkjoin-pool test exit >>outX.txt
call sbt -Dbenchmark.executorServiceType=fast-thread-pool test exit >>outX.txt
call sbt -Dbenchmark.executorServiceType=thread-pool test exit >>outX.txt
