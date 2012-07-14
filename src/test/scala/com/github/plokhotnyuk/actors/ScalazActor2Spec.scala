package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._
import java.util.concurrent.CountDownLatch
import akka.jsr166y.ForkJoinPool
import Scalaz2._
import scalaz.concurrent.Strategy

@RunWith(classOf[JUnitRunner])
class ScalazActor2Spec extends Specification {
  sequential

  implicit val executor = new ForkJoinPool()

  import Strategy.Executor

  "Single-producer sending" in {
    val n = 100000000
    val l = new CountDownLatch(1)
    val a = tickActor(l, n)
    timed("Single-producer sending", n) {
      sendTicks(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = 100000000
    val l = new CountDownLatch(1)
    val a = tickActor(l, n)
    timed("Multi-producer sending", n) {
      for (j <- 1 to CPUs) fork {
        sendTicks(a, n / CPUs)
      }
      l.await()
    }
  }

  "Ping between actors" in {
    val n = 20000000
    val l = new CountDownLatch(2)
    var p1: Actor2[Message] = null
    val p2 = actor2[Message] {
      var i = n / 2

      (m: Message) =>
        p1 ! m
        i -= 1
        if (i == 0) l.countDown()
    }
    p1 = actor2[Message] {
      var i = n / 2

      (m: Message) =>
        p2 ! m
        i -= 1
        if (i == 0) l.countDown()
    }
    timed("Ping between actors", n) {
      p2 ! Message()
      l.await()
    }
  }

  "Max throughput" in {
    val n = 100000000
    val l = new CountDownLatch(halfOfCPUs)
    val as = for (j <- 1 to halfOfCPUs) yield tickActor(l, n / halfOfCPUs)
    timed("Max throughput", n) {
      for (a <- as) fork {
        sendTicks(a, n / halfOfCPUs)
      }
      l.await()
    }
  }

  private[this] def tickActor(l: CountDownLatch, n: Int): Actor2[Message] = actor2[Message] {
    var i = n

    (m: Message) =>
      i -= 1
      if (i == 0) l.countDown()
  }

  private[this] def sendTicks(a: Actor2[Message], n: Int) {
    val m = Message()
    var i = n
    while (i > 0) {
      a ! m
      i -= 1
    }
  }
}