@echo off
call sbt clean test:compile exit >outX.txt
call sbt -Dbenchmark.executorServiceType=akka-forkjoin-pool test >>outX.txt
call sbt -Dbenchmark.executorServiceType=scala-forkjoin-pool test >>outX.txt
call sbt -Dbenchmark.executorServiceType=java-forkjoin-pool test >>outX.txt
call sbt -Dbenchmark.executorServiceType=abq-thread-pool test >>outX.txt
call sbt -Dbenchmark.executorServiceType=lbq-thread-pool test >>outX.txt
call sbt -Dbenchmark.executorServiceType=ltq-thread-pool test >>outX.txt
