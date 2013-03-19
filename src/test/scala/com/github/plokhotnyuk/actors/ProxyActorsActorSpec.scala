package com.github.plokhotnyuk.actors

import api.actor._
import java.util.concurrent._
import concurrent.ExecutionContext
import com.github.plokhotnyuk.actors.BenchmarkSpec._

class ProxyActorsActorSpec extends BenchmarkSpec {
  val executor = lifoForkJoinPool(CPUs)

  "Single-producer sending" in {
    val c = context()
    val n = 10000000
    val l = new CountDownLatch(1)
    val a = tickActor(c, l, n)
    timed(n) {
      sendTicks(a, n)
      l.await()
    }
    actorsFinished(a)
  }

  "Multi-producer sending" in {
    val c = context()
    val n = 10000000
    val l = new CountDownLatch(1)
    val a = tickActor(c, l, n)
    timed(n) {
      for (j <- 1 to CPUs) fork {
        sendTicks(a, n / CPUs)
      }
      l.await()
    }
    actorsFinished(a)
  }

  "Max throughput" in {
    val c = context()
    val n = 10000000
    val l = new CountDownLatch(CPUs)
    val as = for (j <- 1 to CPUs) yield tickActor(c, l, n / CPUs)
    timed(n) {
      for (a <- as) fork {
        sendTicks(a, n / CPUs)
      }
      l.await()
    }
    actorsFinished(as: _*)
  }

  "Ping between actors" in {
    val c = context()
    val n = 10000000
    val l = new CountDownLatch(2)
    val p1 = playerActor(c, l, n / 2)
    val p2 = playerActor(c, l, n / 2)
    timed(n) {
      p1.ping(p2)
      l.await()
    }
    actorsFinished(p1, p2)
  }

  private def tickActor(c: ActorContext, l: CountDownLatch, n: Int): TickActor =
    c.proxyActor[TickActor](args = List(l, n), types = List(classOf[CountDownLatch], classOf[Int]))

  private def sendTicks(a: TickActor, n: Int) {
    var i = n
    while (i > 0) {
      a.countdown()
      i -= 1
    }
  }

  private def playerActor(c: ActorContext, l: CountDownLatch, n: Int): PlayerActor =
    c.proxyActor[PlayerActor](args = List(l, n), types = List(classOf[CountDownLatch], classOf[Int]))

  private def context() = actorContext(ExecutionContext.fromExecutor(executor))
}

class TickActor(val l: CountDownLatch, val n: Int) {
  private var i = n

  def countdown() {
    i -= 1
    if (i == 0) {
      l.countDown()
    }
  }
}

class PlayerActor(val l: CountDownLatch, val n: Int) {
  private var i = n

  def ping(p: PlayerActor) {
    p.ping(this)
    i -= 1
    if (i == 0) {
      l.countDown()
    }
  }
}