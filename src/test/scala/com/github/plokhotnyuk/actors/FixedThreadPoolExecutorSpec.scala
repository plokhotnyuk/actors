package com.github.plokhotnyuk.actors

import org.specs2.mutable.Specification
import java.util.concurrent._
import org.specs2.execute.{Failure, Success, Result}
import java.lang.Thread.UncaughtExceptionHandler
import scala.annotation.tailrec

class FixedThreadPoolExecutorSpec extends Specification {
  val Timeout = 1000 // in millis

  "code executes async" in {
    val latch = new CountDownLatch(1)
    val executor = new FixedThreadPoolExecutor
    try {
      executor.execute(new Runnable() {
        def run() {
          latch.countDown()
        }
      })
      assertCountDown(latch, "Should execute a command")
    } finally {
      executor.shutdown()
      executor.shutdown() // duplicated shutdown is allowed
    }
  }

  "code errors are not catched, worker thread terminates and propagates to handler" in {
    val latch = new CountDownLatch(1)
    val executor = new FixedThreadPoolExecutor(threadCount = 1, handler = new UncaughtExceptionHandler {
      def uncaughtException(t: Thread, e: Throwable) {
        latch.countDown()
      }
    })
    try {
      executor.execute(new Runnable() {
        def run() {
          throw new RuntimeException()
        }
      })
      executor.isTerminated must_== false
      assertCountDown(latch, "Should propagate an exception")
    } finally {
      executor.shutdown()
    }
  }

  "shutdownNow interrupts threads and returns non-completed tasks in order of submitting" in {
    val executor = new FixedThreadPoolExecutor(1)
    val task1 = new Runnable() {
      def run() {
        // do nothing
      }
    }
    val latch = new CountDownLatch(1)
    val task2 = new Runnable() {
      def run() {
        executor.execute(task1)
        executor.execute(this)
        latch.countDown()
        Thread.sleep(Timeout) // should be interrupted
      }
    }
    try {
      executor.execute(task2)
      assertCountDown(latch, "Two new tasks should be submitted during completing a task")
    } finally {
      val remainingTasks = executor.shutdownNow()
      executor.isShutdown must_== true
      remainingTasks.size() must_== 2
      remainingTasks.get(0) must_== task1
      remainingTasks.get(1) must_== task2
    }
  }

  "awaitTermination blocks until all tasks terminates after a shutdown request" in {
    val executor = new FixedThreadPoolExecutor
    try {
      executor.execute(new Runnable() {
        @tailrec
        final def run() {
          run() // hard to interrupt loop
        }
      })
      Thread.sleep(100)
    } finally {
      executor.shutdownNow()
      executor.awaitTermination(Timeout, TimeUnit.MILLISECONDS) must_== false // cannot be successfully shutdown
    }
  }

  "null tasks are not accepted" in {
    val executor = new FixedThreadPoolExecutor
    try {
      executor.execute(null) must throwA[NullPointerException]
    } finally {
      executor.shutdown()
    }
  }

  "terminates safely when shutdownNow called in executed tasks" in {
    val executor = new FixedThreadPoolExecutor
    val latch = new CountDownLatch(1)
    executor.execute(new Runnable() {
      def run() {
        executor.shutdownNow()
        latch.countDown()
      }
    })
    assertCountDown(latch, "Shutdown should be called")
    executor.awaitTermination(Timeout, TimeUnit.MILLISECONDS)
    executor.isTerminated must_== true
  }

  def assertCountDown(latch: CountDownLatch, hint: String): Result = {
    if (latch.await(Timeout, TimeUnit.MILLISECONDS)) Success()
    else Failure("Failed to count down within " + Timeout + " millis: " + hint)
  }
}