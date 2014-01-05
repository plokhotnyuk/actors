package com.github.plokhotnyuk.actors

import java.util.concurrent._
import scala.collection.mutable
import org.specs2.mutable.Specification
import scalaz.concurrent.Actor

class Actors2Spec extends Specification {
  val NumOfMessages = 1000
  val NumOfThreads = 4
  val NumOfMessagesPerThread = NumOfMessages / NumOfThreads
  implicit val executor = Executors.newFixedThreadPool(NumOfThreads)

  "code executes async" in {
    val latch = new CountDownLatch(1)
    val actor = Actor2[Int]((i: Int) => latch.countDown())
    actor ! 1
    assertCountDown(latch)
  }

  "code errors are caught and can be handled" in {
    val latch = new CountDownLatch(1)
    val actor = new Actor[Int]((i: Int) => throw new RuntimeException(), (ex: Throwable) => latch.countDown())
    actor ! 1
    assertCountDown(latch)
  }

  "actors exchange messages without loss" in {
    val latch = new CountDownLatch(NumOfMessages)
    var actor1: Actor2[Int] = null
    val actor2 = Actor2[Int]((i: Int) => actor1 ! i - 1)
    actor1 = Actor2[Int] {
      (i: Int) =>
        if (i == latch.getCount) {
          if (i != 0) actor2 ! i - 1
          latch.countDown()
          latch.countDown()
        }
    }
    actor1 ! NumOfMessages
    assertCountDown(latch)
  }

  "actor handles messages in order of sending by each thread" in {
    val latch = new CountDownLatch(NumOfMessages)
    val actor = countingDownActor(latch)
    for (j <- 1 to NumOfThreads) fork {
      for (i <- 1 to NumOfMessagesPerThread) {
        actor ! (j, i)
      }
    }
    assertCountDown(latch)
  }

  def countingDownActor(latch: CountDownLatch): Actor2[(Int, Int)] = Actor2[(Int, Int)] {
    val ms = mutable.Map[Int, Int]()

    (m: (Int, Int)) =>
      val (j, i) = m
      if (ms.getOrElse(j, 0) + 1 == i) {
        ms.put(j, i)
        latch.countDown()
      }
  }

  def assertCountDown(latch: CountDownLatch, timeout: Long = 1000) =
    latch.await(timeout, TimeUnit.MILLISECONDS) must_== true

  def fork(f: => Unit) {
    new Thread {
      override def run() {
        f
      }
    }.start()
  }
}
