package com.github.plokhotnyuk.actors

import java.util.concurrent._
import scala.collection.mutable
import org.specs2.mutable.Specification
import com.github.plokhotnyuk.actors.Actor2._

class Actor2Spec extends Specification {
  val NumOfMessages = 100000
  val NumOfThreads = 4
  val NumOfMessagesPerThread = NumOfMessages / NumOfThreads
  implicit val executor = Executors.newFixedThreadPool(NumOfThreads)

  "unbounded actor" should {
    "execute code async" in {
      val l = new CountDownLatch(1)
      val a = unboundedActor[Int]((i: Int) => l.countDown())
      a ! 1
      assertCountDown(l)
    }

    "caught code errors to be handled" in {
      val l = new CountDownLatch(1)
      val a = unboundedActor[Int]((i: Int) => throw new RuntimeException(), (ex: Throwable) => l.countDown())
      a ! 1
      assertCountDown(l)
    }

    "exchange messages without loss" in {
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

    "handle messages in order of sending by each thread" in {
      val l = new CountDownLatch(NumOfMessages)
      val a = countingDownUnboundedActor(l)
      for (j <- 1 to NumOfThreads) fork {
        for (i <- 1 to NumOfMessagesPerThread) {
          a ! (j, i)
        }
      }
      assertCountDown(l)
    }
  }

  "bounded actor" should {
    "execute code async" in {
      val l = new CountDownLatch(1)
      val a = boundedActor[Int]((i: Int) => l.countDown(), 1)
      a ! 1
      assertCountDown(l)
    }

    "caught code errors to be handled" in {
      val l = new CountDownLatch(1)
      val a = boundedActor[Int]((i: Int) => throw new RuntimeException(), 1, (ex: Throwable) => l.countDown())
      a ! 1
      assertCountDown(l)
    }

    "exchange messages without loss" in {
      val l = new CountDownLatch(NumOfMessages)
      var a1: Actor2[Int] = null
      val a2 = boundedActor[Int]((i: Int) => a1 ! i - 1, 1)
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

    "handle messages in order of sending by each thread" in {
      val l = new CountDownLatch(NumOfMessages)
      val a = countingDownBoundedActor(l)
      for (j <- 1 to NumOfThreads) fork {
        for (i <- 1 to NumOfMessagesPerThread) {
          a ! (j, i)
        }
      }
      assertCountDown(l)
    }

    "be bounded by positive number" in {
      boundedActor[Int]((i: Int) => (), 0) must throwA[IllegalArgumentException]
    }

    "throw exception on overflow" in {
      val l = new CountDownLatch(1)
      val a = boundedActor[Int]((i: Int) => l.countDown(), 1)
      (1 to 1000).foreach(_ => a ! 1) must throwA[OutOfMessageQueueBoundsException]
      assertCountDown(l)
    }
  }

  private def countingDownUnboundedActor(l: CountDownLatch): Actor2[(Int, Int)] =
    unboundedActor[(Int, Int)] {
      val ms = mutable.Map[Int, Int]()

      (m: (Int, Int)) =>
        val (j, i) = m
        if (ms.getOrElse(j, 0) + 1 == i) {
          ms.put(j, i)
          l.countDown()
        }
    }

  private def countingDownBoundedActor(l: CountDownLatch): Actor2[(Int, Int)] =
    boundedActor[(Int, Int)]({
      val ms = mutable.Map[Int, Int]()

      (m: (Int, Int)) =>
        val (j, i) = m
        if (ms.getOrElse(j, 0) + 1 == i) {
          ms.put(j, i)
          l.countDown()
        }
    }, Int.MaxValue)

  private def assertCountDown(latch: CountDownLatch, timeout: Long = 1000): Boolean =
    latch.await(timeout, TimeUnit.MILLISECONDS) must_== true

  private def fork(f: => Unit): Unit = {
    new Thread {
      override def run() {
        f
      }
    }.start()
  }
}
