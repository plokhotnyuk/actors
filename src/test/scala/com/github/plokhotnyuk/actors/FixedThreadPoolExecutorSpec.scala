package com.github.plokhotnyuk.actors

import collection.JavaConversions._
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicBoolean
import org.scalatest._

class FixedThreadPoolExecutorSpec extends FreeSpec with MustMatchers {
  val NumOfTasks = 1000
  val Timeout = 1000 // in millis

  "all submitted before shutdown tasks executes async" in {
    withExecutor(new FixedThreadPoolExecutor) {
      e =>
        val taskRequests = new Semaphore(0)
        val latch = new CountDownLatch(NumOfTasks)
        for (i <- 1 to NumOfTasks) {
          e.execute(new Runnable() {
            taskRequests.release()
            def run() {
              latch.countDown()
            }
          })
        }
        taskRequests.acquire(NumOfTasks)
        e.shutdown()
        assertCountDown(latch, "Should execute a command")
    }
  }

  "errors of tasks are caught and can be handled without interruption of worker threads" in {
    val latch = new CountDownLatch(NumOfTasks)
    withExecutor(new FixedThreadPoolExecutor(poolSize = 1, // single thread to check if it wasn't terminated later
      onError = _ => latch.countDown())) {
      e =>
        for (i <- 1 to NumOfTasks) {
          e.execute(new Runnable() {
            def run() {
              throw new RuntimeException()
            }
          })
        }
        e.isTerminated must be (false)
        assertCountDown(latch, "Should propagate an exception")
    }
  }

  "shutdownNow interrupts threads and returns non-completed tasks in order of submitting" in {
    withExecutor(new FixedThreadPoolExecutor(1)) {
      e =>
        val task1 = new Runnable() {
          def run() {
            // do nothing
          }
        }
        val latch = new CountDownLatch(1)
        val task2 = new Runnable() {
          def run() {
            e.execute(task1)
            e.execute(this)
            latch.countDown()
            Thread.sleep(Timeout) // should be interrupted
          }
        }
        e.execute(task2)
        assertCountDown(latch, "Two new tasks should be submitted during completing a task")
        e.shutdownNow() must be (new java.util.LinkedList(Seq(task1, task2)))
        e.isShutdown must be (true)
    }
  }

  "awaitTermination blocks until all tasks terminates after a shutdown request" in {
    withExecutor(new FixedThreadPoolExecutor) {
      e =>
        val running = new AtomicBoolean(true)
        val semaphore = new Semaphore(0)
        e.execute(new Runnable() {
          final def run() {
            semaphore.release()
            while (running.get) {
              // hard to interrupt loop
            }
          }
        })
        semaphore.acquire()
        e.shutdownNow() must be ('empty)
        e.awaitTermination(1, TimeUnit.MILLISECONDS) must be (false)
        running.lazySet(false)
        e.awaitTermination(Timeout, TimeUnit.MILLISECONDS) must be (true)
    }
  }

  "null tasks are not accepted" in {
    withExecutor(new FixedThreadPoolExecutor) {
      e =>
        evaluating {
          e.execute(null)
        } must produce[NullPointerException]
    }
  }

  "terminates safely when shutdownNow called during task execution" in {
    withExecutor(new FixedThreadPoolExecutor) {
      e =>
        val latch = new CountDownLatch(1)
        e.execute(new Runnable() {
          def run() {
            e.shutdownNow()
            latch.countDown()
          }
        })
        assertCountDown(latch, "Shutdown should be called")
        e.awaitTermination(Timeout, TimeUnit.MILLISECONDS) must be (true)
        e.isTerminated must be (true)
    }
  }

  "duplicated shutdownNow/shutdown is allowed" in {
    withExecutor(new FixedThreadPoolExecutor) {
      e =>
        e.shutdownNow()
        e.shutdown()
        e.shutdownNow()
        e.shutdown()
    }
  }

  "all tasks which are submitted after shutdown are rejected by default" in {
    withExecutor(new FixedThreadPoolExecutor) {
      e =>
        e.shutdown()
        val executed = new AtomicBoolean(false)
        evaluating {
          e.execute(new Runnable() {
            def run() {
              executed.set(true) // should not be executed
            }
          })
        } must produce[RejectedExecutionException]
        executed.get must be (false)
    }
  }

  "all tasks which are submitted after shutdown can be handled by onReject" in {
    val latch = new CountDownLatch(1)
    withExecutor(new FixedThreadPoolExecutor(onReject = _ => latch.countDown())) {
      e =>
        e.shutdown()
        val executed = new AtomicBoolean(false)
        e.execute(new Runnable() {
          def run() {
            executed.set(true) // Should not be executed
          }
        })
        e.shutdownNow() must be ('empty)
        executed.get must be (false)
        assertCountDown(latch, "OnReject should be called")
    }
  }

  def withExecutor(executor: ExecutorService)(testCode: ExecutorService => Any) {
    try {
      testCode(executor)
    } finally {
      executor.shutdownNow()
      executor.awaitTermination(Timeout, TimeUnit.MILLISECONDS)
    }
  }

  def assertCountDown(latch: CountDownLatch, hint: String) {
    latch.await(Timeout, TimeUnit.MILLISECONDS) must be (true)
  }
}
