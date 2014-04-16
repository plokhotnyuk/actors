package com.github.plokhotnyuk.actors

import java.util.concurrent._
import scala.collection.mutable
import org.specs2.mutable.Specification
import com.github.plokhotnyuk.actors.Actor2._

class UnboundedActors2Spec extends Specification {
  val NumOfMessages = 100000
  val NumOfThreads = 4
  val NumOfMessagesPerThread = NumOfMessages / NumOfThreads
  implicit val executor = Executors.newFixedThreadPool(NumOfThreads)

  "code executes async" in {
    val l = new CountDownLatch(1)
    val a = unboundedActor[Int]((i: Int) => l.countDown())
    a ! 1
    assertCountDown(l)
  }

  "code errors are caught and can be handled" in {
    val l = new CountDownLatch(1)
    val a = unboundedActor[Int]((i: Int) => throw new RuntimeException(), (ex: Throwable) => l.countDown())
    a ! 1
    assertCountDown(l)
  }

  "actors exchange messages without loss" in {
    val l = new CountDownLatch(NumOfMessages)
    var a1: Actor2[Int] = null
    val a2 = unboundedActor[Int]((i: Int) => a1 ! i - 1)
    a1 = unboundedActor[Int] {
      (i: Int) =>
        if (i == l.getCount) {
          if (i != 0) a2 ! i - 1
          l.countDown()
          l.countDown()
        }
    }
    a1 ! NumOfMessages
    assertCountDown(l)
  }

  "actor handles messages in order of sending by each thread" in {
    val l = new CountDownLatch(NumOfMessages)
    val a = countingDownActor(l)
    for (j <- 1 to NumOfThreads) fork {
      for (i <- 1 to NumOfMessagesPerThread) {
        a ! (j, i)
      }
    }
    assertCountDown(l)
  }

  private def countingDownActor(l: CountDownLatch): Actor2[(Int, Int)] =
    unboundedActor[(Int, Int)] {
      val ms = mutable.Map[Int, Int]()

      (m: (Int, Int)) =>
        val (j, i) = m
        if (ms.getOrElse(j, 0) + 1 == i) {
          ms.put(j, i)
          l.countDown()
        }
    }

  private def assertCountDown(latch: CountDownLatch, timeout: Long = 1000): Unit =
    latch.await(timeout, TimeUnit.MILLISECONDS) must_== true

  private def fork(f: => Unit): Unit = {
    new Thread {
      override def run() {
        f
      }
    }.start()
  }
}
