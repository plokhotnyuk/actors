package com.github.plokhotnyuk.actors

import collection.JavaConversions._
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicBoolean
import org.specs2.mutable.Specification

class FixedThreadPoolExecutorSpec extends Specification {
  val NumOfTasks = 1000
  val Timeout = 1000 // in millis

  "can execute tasks in parallel threads" in {
    val n = Runtime.getRuntime.availableProcessors()
    withExecutor(new FixedThreadPoolExecutor(n, spin = -1)) { //TODO: fix to pass with spin = 0
      e =>
        val latch = new CountDownLatch(n)
        for (i <- 1 to n) {
          e.execute(new Runnable() {
            def run(): Unit = {
              Thread.sleep(Timeout - 100)
              latch.countDown()
            }
          })
        }
        assertCountDown(latch)
    }
  }

  "all submitted before shutdown tasks executes async" in {
    withExecutor(new FixedThreadPoolExecutor) {
      e =>
        val taskRequests = new Semaphore(0)
        val latch = new CountDownLatch(NumOfTasks)
        for (i <- 1 to NumOfTasks) {
          e.execute(new Runnable() {
            taskRequests.release()
            def run(): Unit = latch.countDown()
          })
        }
        taskRequests.acquire(NumOfTasks)
        e.shutdown()
        assertCountDown(latch)
    }
  }

  "errors of tasks are caught and can be handled without interruption of worker threads" in {
    val latch = new CountDownLatch(NumOfTasks)
    withExecutor(new FixedThreadPoolExecutor(poolSize = 1, // single thread to check if it wasn't terminated later
      onError = _ => latch.countDown())) {
      e =>
        for (i <- 1 to NumOfTasks) {
          e.execute(new Runnable() {
            def run(): Unit = throw new RuntimeException()
          })
        }
        e.isTerminated must_== false
        assertCountDown(latch)
    }
  }

  "shutdownNow interrupts threads and returns non-completed tasks in order of submitting" in {
    withExecutor(new FixedThreadPoolExecutor(1, onError = { case _: InterruptedException => })) {
      e =>
        val task1 = new Runnable() {
          def run(): Unit = () // do nothing
        }
        val latch = new CountDownLatch(1)
        val task2 = new Runnable() {
          def run(): Unit = {
            e.execute(task1)
            e.execute(this)
            latch.countDown()
            Thread.sleep(Timeout) // should be interrupted
          }
        }
        e.execute(task2)
        assertCountDown(latch)
        e.shutdownNow() must_== new java.util.LinkedList(Seq(task1, task2))
        e.isShutdown must_== true
        e.shutdownNow() must_== new java.util.LinkedList
    }
  }

  "awaitTermination blocks until all tasks terminates after a shutdown request" in {
    withExecutor(new FixedThreadPoolExecutor) {
      e =>
        val running = new AtomicBoolean(true)
        val semaphore = new Semaphore(0)
        e.execute(new Runnable() {
          final def run(): Unit = {
            semaphore.release()
            while (running.get) {
              // hard to interrupt loop
            }
          }
        })
        semaphore.acquire()
        e.shutdownNow() must beEmpty
        e.awaitTermination(1, TimeUnit.MILLISECONDS) must_== false
        running.lazySet(false)
        e.awaitTermination(Timeout, TimeUnit.MILLISECONDS) must_== true
    }
  }

  "null tasks are not accepted" in {
    withExecutor(new FixedThreadPoolExecutor) {
      e =>
        e.execute(null) must throwA[NullPointerException]
    }
  }

  "poolSize less than 1 is not allowed" in {
    new FixedThreadPoolExecutor(0) must throwA[IllegalArgumentException]
  }

  "terminates safely when shutdownNow called during task execution" in {
    withExecutor(new FixedThreadPoolExecutor) {
      e =>
        val latch = new CountDownLatch(1)
        e.execute(new Runnable() {
          def run(): Unit = {
            e.shutdownNow()
            latch.countDown()
          }
        })
        assertCountDown(latch)
        e.awaitTermination(Timeout, TimeUnit.MILLISECONDS) must_== true
        e.isTerminated must_== true
    }
  }

  "duplicated shutdownNow/shutdown is allowed" in {
    withExecutor(new FixedThreadPoolExecutor) {
      e =>
        e.shutdownNow()
        e.shutdown()
        e.shutdownNow()
        e.shutdown()
        e.shutdownNow() must beEmpty
    }
  }

  "all tasks which are submitted after shutdown are rejected by default" in {
    withExecutor(new FixedThreadPoolExecutor) {
      e =>
        e.shutdown()
        val executed = new AtomicBoolean(false)
        e.execute(new Runnable() {
          def run(): Unit = executed.set(true) // should not be executed
        }) must throwA[RejectedExecutionException]
        executed.get must_== false
    }
  }

  "all tasks which are submitted after shutdown can be handled by onReject" in {
    val latch = new CountDownLatch(1)
    withExecutor(new FixedThreadPoolExecutor(onReject = _ => latch.countDown())) {
      e =>
        e.shutdown()
        val executed = new AtomicBoolean(false)
        e.execute(new Runnable() {
          def run(): Unit = executed.set(true) // Should not be executed
        })
        e.shutdownNow() must beEmpty
        executed.get must_== false
        assertCountDown(latch)
    }
  }

  "toString should print pool name, status and size" in {
    withExecutor(new FixedThreadPoolExecutor(poolSize = 3, name = "Pool")) {
      e =>
        e.toString must contain("[Running], pool size = 3, name = Pool")
    }
  }

  private def withExecutor[A](executor: ExecutorService)(testCode: ExecutorService => A) =
    try {
      testCode(executor)
    } finally {
      executor.shutdownNow()
      executor.awaitTermination(Timeout, TimeUnit.MILLISECONDS)
    }

  private def assertCountDown(latch: CountDownLatch) =
    (latch.await(Timeout, TimeUnit.MILLISECONDS) must_== true) and (latch.getCount must_== 0)
}
