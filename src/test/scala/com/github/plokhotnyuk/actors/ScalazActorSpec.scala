package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._
import scalaz._
import concurrent.{Actor, Strategy}
import Scalaz._
import java.util.concurrent.CountDownLatch
import akka.jsr166y.ForkJoinPool

@RunWith(classOf[JUnitRunner])
class ScalazActorSpec extends Specification {
  sequential

  implicit val executor = new ForkJoinPool()

  import Strategy.Executor

  "Single-producer sending" in {
    val n = 40000000
    val l = new CountDownLatch(1)
    val a = tickActor(l, n)
    timed("Single-producer sending", n) {
      sendTicks(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = 40000000
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
    val l = new CountDownLatch(1)
    var p1: Actor[Message] = null
    val p2 = actor[Message] {
      var i = n / 2

      (m: Message) =>
        p1 ! m
        i -= 1
        if (i == 0) l.countDown()
    }
    p1 = actor[Message] {
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
    val n = 40000000
    val l = new CountDownLatch(halfOfCPUs)
    val as = for (j <- 1 to halfOfCPUs) yield tickActor(l, n / halfOfCPUs)
    timed("Max throughput", n) {
      for (a <- as) fork {
        sendTicks(a, n / halfOfCPUs)
      }
      l.await()
    }
  }

  private[this] def tickActor(l: CountDownLatch, n: Int): Actor[Message] = actor[Message] {
    var i = n

    (m: Message) =>
      i -= 1
      if (i == 0) {
        l.countDown()
      }
  }

  private[this] def sendTicks(a: Actor[Message], n: Int) {
    val t = Message()
    var i = n
    while (i > 0) {
      a ! t
      i -= 1
    }
  }
}