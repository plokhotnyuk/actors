@echo off
call mvn -B -Dbenchmark.executorServiceType=fifo-forkjoin-pool clean test >outX.txt 
call mvn -B -Dbenchmark.executorServiceType=lifo-forkjoin-pool test >>outX.txt
call mvn -B -Dbenchmark.executorServiceType=fast-thread-pool test >>outX.txt
call mvn -B -Dbenchmark.executorServiceType=thread-pool test >>outX.txt