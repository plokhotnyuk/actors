package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._
import java.util.concurrent.CountDownLatch

@RunWith(classOf[JUnitRunner])
class ThreadBasedActorTest extends Specification {

  "Single-producer sending" in {
    val n = 100000000
    timed("Single-producer sending", n) {
      val l = new CountDownLatch(1)
      val a = tickActor(l, n)
      sendTicks(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = 100000000
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
    val n = 50000000
    timed("Ping between actors", n) {
      val l = new CountDownLatch(1)
      var p1: ThreadBasedActor[Ball] = null
      val p2 = new ThreadBasedActor[Ball]({
        (b: Ball) => b match {
          case Ball(0) => l.countDown()
          case Ball(i) => p1 ! Ball(i - 1)
        }
      })
      p1 = new ThreadBasedActor[Ball]({
        (b: Ball) => b match {
          case Ball(0) => l.countDown()
          case Ball(i) => p2 ! Ball(i - 1)
        }
      })
      p2 ! Ball(n)
      l.await()
    }
  }

  "Max throughput" in {
    val n = 100000000
    timed("Max throughput", n) {
      val l = new CountDownLatch(halfOfCPUs)
      for (j <- 1 to halfOfCPUs) fork {
        val a = tickActor(l, n / halfOfCPUs)
        sendTicks(a, n / halfOfCPUs)
      }
      l.await()
    }
  }

  private[this] def tickActor(l: CountDownLatch, n: Int): ThreadBasedActor[Tick] = new ThreadBasedActor[Tick]({
    var i = n
    (t: Tick) =>
      i -= 1
      if (i == 0) {
        l.countDown()
      }
  })

  private[this] def sendTicks(a: ThreadBasedActor[Tick], n: Int) {
    val t = Tick()
    var i = n
    while (i > 0) {
      a ! t
      i -= 1
    }
  }
}