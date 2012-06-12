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
class ScalazActorTest extends Specification {
  implicit val executor = new ForkJoinPool()

  import Strategy.Executor

  "Single-producer sending" in {
    val n = 40000000
    timed("Single-producer sending", n) {
      val l = new CountDownLatch(1)
      val a = tickActor(l, n)
      sendTicks(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = 40000000
    timed("Multi-producer sending", n) {
      val l = new CountDownLatch(1)
      val a = tickActor(l, n)
      for (j <- 1 to CPUs) fork {
        sendTicks(a, n / CPUs)
      }
      l.await()
    }
  }

  "Ping between actors" in {
    val n = 20000000
    timed("Ping between actors", n) {
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
      p2 ! Message()
      l.await()
    }
  }

  "Max throughput" in {
    val n = 40000000
    timed("Max throughput", n) {
      val l = new CountDownLatch(halfOfCPUs)
      for (j <- 1 to halfOfCPUs) fork {
        val a = tickActor(l, n / halfOfCPUs)
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