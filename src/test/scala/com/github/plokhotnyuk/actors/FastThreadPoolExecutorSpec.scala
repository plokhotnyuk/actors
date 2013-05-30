package com.github.plokhotnyuk.actors

import org.specs2.mutable.Specification
import java.util.concurrent._
import org.specs2.execute.{Failure, Success, Result}
import java.lang.Thread.UncaughtExceptionHandler
import scala.annotation.tailrec

class FastThreadPoolExecutorSpec extends Specification {
  val Timeout = 1000 // in millis

  "code executes async" in {
    val latch = new CountDownLatch(1)
    val executor = new FastThreadPoolExecutor
    executor.execute(new Runnable() {
      def run() {
        latch.countDown()
      }
    })
    assertCountDown(latch, "Should execute a task")
    executor.shutdown()
  }

  "code errors are not catched, worker thread terminates and propagates to handler" in {
    val latch = new CountDownLatch(1)
    val executor = new FastThreadPoolExecutor(threadCount = 1, handler = new UncaughtExceptionHandler {
      def uncaughtException(t: Thread, e: Throwable) {
        latch.countDown()
      }
    })
    executor.execute(new Runnable() {
      def run() {
        throw new RuntimeException()
      }
    })
    executor.isTerminated must_== false
    assertCountDown(latch, "Should propagate an exception")
    executor.shutdown()
  }

  "shutdownNow interrupts threads and returns non-completed tasks" in {
    val executor = new FastThreadPoolExecutor
    val task = new Runnable() {
      def run() {
        executor.execute(this)
        Thread.sleep(10000)
      }
    }
    executor.execute(task)
    val remainingTasks = executor.shutdownNow()
    remainingTasks.size() must_== 1
    remainingTasks.get(0) must_== task
    executor.isShutdown must_== true
  }

  "awaitTermination blocks until all tasks terminates after a shutdown request" in {
    val executor = new FastThreadPoolExecutor
    executor.execute(new Runnable() {
      @tailrec
      final def run() {
        run() // hard to interrupt loop
      }
    })
    Thread.sleep(100)
    executor.shutdownNow()
    executor.awaitTermination(Timeout, TimeUnit.MILLISECONDS) must_== false // cannot be successfully shutdown
  }

  def assertCountDown(latch: CountDownLatch, hint: String): Result = {
    if (latch.await(Timeout, TimeUnit.MILLISECONDS)) Success()
    else Failure("Failed to count down within " + Timeout + " millis: " + hint)
  }
}