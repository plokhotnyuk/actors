package com.github.plokhotnyuk.actors

import com.github.plokhotnyuk.actors.Actor2._
import java.util.concurrent.CountDownLatch
import com.github.plokhotnyuk.actors.BenchmarkSpec._
import scalaz.concurrent.Strategy

class ScalazActor2Spec extends BenchmarkSpec {
  val executorService = createExecutorService()
  implicit val strategy = new Strategy {
    private val e = executorService

    def apply[A](a: => A): () => A = {
      e.execute(new Runnable() {
        def run(): Unit = a
      })
      null
    }
  }

  "Enqueueing" in {
    val n = 50000000
    val l1 = new CountDownLatch(1)
    val l2 = new CountDownLatch(1)
    val a = blockableCountActor(l1, l2, n)
    footprintedAndTimed(n) {
      sendMessages(a, n)
    }
    l1.countDown()
    l2.await()
  }

  "Dequeueing" in {
    val n = 50000000
    val l1 = new CountDownLatch(1)
    val l2 = new CountDownLatch(1)
    val a = blockableCountActor(l1, l2, n)
    sendMessages(a, n)
    timed(n) {
      l1.countDown()
      l2.await()
    }
  }

  "Initiation" in {
    footprintedAndTimedCollect(10000000)(() => actor[Message](_ => ()))
  }

  "Single-producer sending" in {
    val n = 32000000
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    timed(n) {
      sendMessages(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = roundToParallelism(28000000)
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    timed(n) {
      for (j <- 1 to parallelism) fork {
        sendMessages(a, n / parallelism)
      }
      l.await()
    }
  }

  "Max throughput" in {
    val n = roundToParallelism(72000000)
    val l = new CountDownLatch(parallelism)
    val as = for (j <- 1 to parallelism) yield countActor(l, n / parallelism)
    timed(n) {
      for (a <- as) fork {
        sendMessages(a, n / parallelism)
      }
      l.await()
    }
  }

  "Ping latency" in {
    ping(10000000, 1)
  }

  "Ping throughput 10K" in {
    ping(28000000, 10000)
  }

  def ping(n: Int, p: Int): Unit = {
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
    timed(n, printAvgLatency = p == 1) {
      as.foreach(_ ! Message())
      l.await()
    }
  }

  def shutdown(): Unit = fullShutdown(executorService)

  private def blockableCountActor(l1: CountDownLatch, l2: CountDownLatch, n: Int): Actor2[Message] =
    actor[Message] {
      var blocked = true
      var i = n - 1
      (m: Message) =>
        if (blocked) {
          l1.await()
          blocked = false
        } else {
          i -= 1
          if (i == 0) l2.countDown()
        }
    }

  private def countActor(l: CountDownLatch, n: Int): Actor2[Message] =
    actor[Message] {
      var i = n
      (m: Message) =>
        i -= 1
        if (i == 0) l.countDown()
    }

  private def sendMessages(a: Actor2[Message], n: Int): Unit = {
    val t = Message()
    var i = n
    while (i > 0) {
      a ! t
      i -= 1
    }
  }
}
