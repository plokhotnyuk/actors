package com.github.plokhotnyuk.actors

import api.actor._
import java.util.concurrent._
import concurrent.ExecutionContext
import com.github.plokhotnyuk.actors.BenchmarkSpec._

class ProxyActorsActorSpec extends BenchmarkSpec {
  val executorService = createExecutorService()
  val context = actorContext(ExecutionContext.fromExecutorService(executorService))

  "Single-producer sending" in {
    val n = 10000000
    val l = new CountDownLatch(1)
    val a = tickActor(l, n)
    timed(n) {
      sendTicks(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = 10000000
    val l = new CountDownLatch(1)
    val a = tickActor(l, n)
    timed(n) {
      for (j <- 1 to parallelism) fork {
        sendTicks(a, n / parallelism)
      }
      l.await()
    }
  }

  "Max throughput" in {
    val n = 10000000
    val l = new CountDownLatch(parallelism)
    val as = for (j <- 1 to parallelism) yield tickActor(l, n / parallelism)
    timed(n) {
      for (a <- as) fork {
        sendTicks(a, n / parallelism)
      }
      l.await()
    }
  }

  "Ping between actors" in {
    val n = 10000000
    val l = new CountDownLatch(2)
    val p1 = playerActor(l, n / 2)
    val p2 = playerActor(l, n / 2)
    timed(n) {
      p1.ping(p2)
      l.await()
    }
  }

  override def shutdown() {
    executorService.shutdown()
  }

  private def tickActor(l: CountDownLatch, n: Int): TickActor =
    context.proxyActor[TickActor](args = List(l, n), types = List(classOf[CountDownLatch], classOf[Int]))

  private def sendTicks(a: TickActor, n: Int) {
    var i = n
    while (i > 0) {
      a.countdown()
      i -= 1
    }
  }

  private def playerActor(l: CountDownLatch, n: Int): PlayerActor =
    context.proxyActor[PlayerActor](args = List(l, n), types = List(classOf[CountDownLatch], classOf[Int]))
}

class TickActor(val l: CountDownLatch, val n: Int) {
  private var i = n

  def countdown() {
    i -= 1
    if (i == 0) l.countDown()
  }
}

class PlayerActor(val l: CountDownLatch, val n: Int) {
  private var i = n

  def ping(p: PlayerActor) {
    p.ping(this)
    i -= 1
    if (i == 0) l.countDown()
  }
}