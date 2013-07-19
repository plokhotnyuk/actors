package com.github.plokhotnyuk.actors

import com.github.plokhotnyuk.actors.Actor2._
import java.util.concurrent.CountDownLatch
import com.github.plokhotnyuk.actors.BenchmarkSpec._
import org.specs2.execute.Result
import scalaz.concurrent.Strategy

class ScalazActor2Spec extends BenchmarkSpec {
  val executorService = createExecutorService()
  implicit val strategy = new Strategy {
    private val e = executorService

    def apply[A](a: => A) = {
      e.execute(new Runnable() {
        def run() {
          a
        }
      })
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

  "Ping latency" in {
    ping(20000000, 1)
  }

  "Ping throughput 10K" in {
    ping(20000000, 10000)
  }

  "Initiation 1M" in {
    footprintedCollect(1000000)(_ => actor[Message] {
      (m: Message) =>
    })
  }

  def ping(n: Int, p: Int): Result = {
    val l = new CountDownLatch(p * 2)
    val as = (1 to p).map {
      _ =>
        var a1: Actor2[Message] = null
        val a2 = actor[Message] {
          var i = n / p / 2

          (m: Message) =>
            if (i > 0) a1 ! m
            i -= 1
            if (i == 0) l.countDown()
        }
        a1 = actor[Message] {
          var i = n / p / 2

          (m: Message) =>
            if (i > 0) a2 ! m
            i -= 1
            if (i == 0) l.countDown()
        }
        a2
    }
    timed(n) {
      as.foreach(_ ! Message())
      l.await()
    }
  }

  def shutdown() {
    fullShutdown(executorService)
  }

  private def tickActor(l: CountDownLatch, n: Int): Actor2[Message] = actor[Message] {
    var i = n

    (m: Message) =>
      i -= 1
      if (i == 0) l.countDown()
  }

  private def sendTicks(a: Actor2[Message], n: Int) {
    val t = Message()
    var i = n
    while (i > 0) {
      a ! t
      i -= 1
    }
  }
}