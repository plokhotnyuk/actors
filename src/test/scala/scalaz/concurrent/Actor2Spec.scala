package scalaz.concurrent

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger
import org.specs2.mutable.Specification
import scala.collection.mutable
import scalaz.concurrent.Actor2._

class Actor2Spec extends Specification {
  val NumOfMessages = 100000
  val NumOfThreads = 4
  val NumOfMessagesPerThread = NumOfMessages / NumOfThreads
  implicit val executor = Executors.newFixedThreadPool(NumOfThreads)

  "unbounded actor" should {
    "execute code async" in {
      val l = new CountDownLatch(1)
      val a = unboundedActor((_: Int) => l.countDown())
      a ! 1
      assertCountDown(l)
    }

    "caught code errors to be handled" in {
      val l = new CountDownLatch(1)
      val a = unboundedActor((_: Int) => throw new RuntimeException(), (ex: Throwable) => l.countDown())
      a ! 1
      assertCountDown(l)
    }

    "exchange messages without loss" in {
      val l = new CountDownLatch(NumOfMessages)
      var a1: Actor2[Int] = null
      val a2 = unboundedActor((i: Int) => a1 ! i - 1)
      a1 = unboundedActor {
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
      val a = boundedActor(1, (_: Int) => l.countDown())
      a ! 1
      assertCountDown(l)
    }

    "caught code errors to be handled" in {
      val l = new CountDownLatch(1)
      val a = boundedActor(1, (_: Int) => throw new RuntimeException(), (ex: Throwable) => l.countDown())
      a ! 1
      assertCountDown(l)
    }

    "exchange messages without loss" in {
      val l = new CountDownLatch(NumOfMessages)
      var a1: Actor2[Int] = null
      val a2 = boundedActor(1, (i: Int) => a1 ! i - 1)
      a1 = boundedActor(1, {
        (i: Int) =>
          if (i == l.getCount) {
            if (i != 0) a2 ! i - 1
            l.countDown()
            l.countDown()
          }
      })
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
      boundedActor(0, (_: Int) => ()) must throwA[IllegalArgumentException]
    }

    "handle overflow" in {
      val i = new AtomicInteger
      val a = boundedActor(1, (_: Int) => (), onOverflow = (_: Int) => i.incrementAndGet())
      (1 to NumOfMessages).foreach(a ! _)
      i.get must be greaterThan 0
    }
  }

  private def countingDownUnboundedActor(l: CountDownLatch): Actor2[(Int, Int)] =
    unboundedActor {
      val ms = mutable.Map[Int, Int]()
      (m: (Int, Int)) =>
        val (j, i) = m
        if (ms.getOrElse(j, 0) + 1 == i) {
          ms.put(j, i)
          l.countDown()
        }
    }

  private def countingDownBoundedActor(l: CountDownLatch): Actor2[(Int, Int)] =
    boundedActor(Int.MaxValue, {
      val ms = mutable.Map[Int, Int]()
      (m: (Int, Int)) =>
        val (j, i) = m
        if (ms.getOrElse(j, 0) + 1 == i) {
          ms.put(j, i)
          l.countDown()
        }
    })

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
