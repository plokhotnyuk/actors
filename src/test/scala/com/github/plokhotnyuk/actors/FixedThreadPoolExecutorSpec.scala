package com.github.plokhotnyuk.actors

import org.specs2.mutable.Specification
import org.specs2.execute.{Failure, Success, Result}
import java.util
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicBoolean
import java.lang.Thread.UncaughtExceptionHandler
import scala.collection.JavaConversions._

class FixedThreadPoolExecutorSpec extends Specification {
  val Timeout = 1000 // in millis

  "task code executes async" in {
    testWith(new FixedThreadPoolExecutor) {
      e =>
        val latch = new CountDownLatch(1)
        e.execute(new Runnable() {
          def run() {
            latch.countDown()
          }
        })
        assertCountDown(latch, "Should execute a command")
    }
  }

  "task code errors are caught and can be handled without interruption of worker threads" in {
    val latch = new CountDownLatch(1)
    testWith(new FixedThreadPoolExecutor(threadCount = 1, onError = {
      _ => latch.countDown()
    })) {
      e =>
        e.execute(new Runnable() {
          def run() {
            throw new RuntimeException()
          }
        })
        e.isTerminated must_== false
        assertCountDown(latch, "Should propagate an exception")
    }
  }

  "shutdownNow interrupts threads and returns non-completed tasks in order of submitting" in {
    testWith(new FixedThreadPoolExecutor(1)) {
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
        e.shutdownNow() must_== new util.LinkedList(Seq(task1, task2))
        e.isShutdown must_== true
    }
  }

  "awaitTermination blocks until all tasks terminates after a shutdown request" in {
    testWith(new FixedThreadPoolExecutor) {
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
        e.shutdownNow() must beEmpty
        e.awaitTermination(1, TimeUnit.MILLISECONDS) must_== false
        running.lazySet(false)
        e.awaitTermination(Timeout, TimeUnit.MILLISECONDS) must_== true
    }
  }

  "null tasks are not accepted" in {
    testWith(new FixedThreadPoolExecutor) {
      e =>
        e.execute(null) must throwA[NullPointerException]
    }
  }

  "terminates safely when shutdownNow called during task execution" in {
    testWith(new FixedThreadPoolExecutor) {
      e =>
        val latch = new CountDownLatch(1)
        e.execute(new Runnable() {
          def run() {
            e.shutdownNow()
            latch.countDown()
          }
        })
        assertCountDown(latch, "Shutdown should be called")
        e.awaitTermination(Timeout, TimeUnit.MILLISECONDS) must_== true
        e.isTerminated must_== true
    }
  }

  "duplicated shutdownNow/shutdown is allowed" in {
    testWith(new FixedThreadPoolExecutor) {
      e =>
        e.shutdownNow()
        e.shutdown()
        e.shutdownNow() must not(throwA[Throwable])
        e.shutdown() must not(throwA[Throwable])
    }
  }

  "all tasks which are submitted after shutdown are rejected by default" in {
    testWith(new FixedThreadPoolExecutor) {
      e =>
        e.shutdown()
        val executed = new AtomicBoolean(false)
        e.execute(new Runnable() {
          def run() {
            executed.set(true) // should not be executed
          }
        }) must throwA[RejectedExecutionException]
        executed.get must_== false
    }
  }

  "all tasks which are submitted after shutdown are discarded if rejectAfterShutdown was set to false" in {
    testWith(new FixedThreadPoolExecutor(rejectAfterShutdown = false)) {
      e =>
        e.shutdown()
        val executed = new AtomicBoolean(false)
        e.execute(new Runnable() {
          def run() {
            executed.set(true) // Should not be executed
          }
        })
        e.shutdownNow() must beEmpty
        executed.get must_== false
    }
  }

  def testWith(executor: ExecutorService)(testCode: ExecutorService => Result): Result = {
    try {
      testCode(executor)
    } finally {
      executor.shutdownNow()
      executor.awaitTermination(Timeout, TimeUnit.MILLISECONDS)
    }
  }

  def assertCountDown(latch: CountDownLatch, hint: String): Result = {
    if (latch.await(Timeout, TimeUnit.MILLISECONDS)) Success()
    else Failure("Failed to count down within " + Timeout + " millis: " + hint)
  }
}