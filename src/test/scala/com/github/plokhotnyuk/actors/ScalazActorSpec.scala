package com.github.plokhotnyuk.actors

import scalaz.concurrent._
import scalaz.concurrent.Actor._
import java.util.concurrent.CountDownLatch
import com.github.plokhotnyuk.actors.BenchmarkSpec._

class ScalazActorSpec extends BenchmarkSpec {

  val executorService = createExecutorService()
  implicit val strategy = new Strategy {
    private val service = executorService

    def apply[A](a: => A) = {
      if (!service.isShutdown) {
        service.execute(new Runnable() {
          def run = a
        })
      }
      () => null.asInstanceOf[A]
    }
  }

  "Single-producer sending" in {
    val n = 100000000
    val l = new CountDownLatch(1)
    val a = tickActor(l, n)
    timed(n) {
      sendTicks(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = 50000000
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
    val n = 200000000
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
    val l = new CountDownLatch(1)
    var a1: Actor[Message] = null
    val a2 = actor[Message] {
      var i = n / 2

      (m: Message) =>
        a1 ! m
        i -= 1
        if (i == 0) l.countDown()
    }
    a1 = actor[Message] {
      var i = n / 2

      (m: Message) =>
        a2 ! m
        i -= 1
        if (i == 0) l.countDown()
    }
    timed(n) {
      a2 ! Message()
      l.await()
    }
  }

  def shutdown() {
    executorService.shutdown()
  }

  private def tickActor(l: CountDownLatch, n: Int): Actor[Message] = actor[Message] {
    var i = n

    (m: Message) =>
      i -= 1
      if (i == 0) l.countDown()
  }

  private def sendTicks(a: Actor[Message], n: Int) {
    val t = Message()
    var i = n
    while (i > 0) {
      a ! t
      i -= 1
    }
  }
}