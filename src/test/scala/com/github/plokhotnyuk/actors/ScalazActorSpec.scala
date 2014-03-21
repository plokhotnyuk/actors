package com.github.plokhotnyuk.actors

import scalaz.concurrent.Actor
import scalaz.concurrent.Actor._
import java.util.concurrent.CountDownLatch
import com.github.plokhotnyuk.actors.BenchmarkSpec._
import scalaz.concurrent.Strategy

class ScalazActorSpec extends BenchmarkSpec {
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
    val n = 80000000
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
    val n = 80000000
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
    footprintedAndTimedCollect(20000000)(() => actor[Message](_ => ()))
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
    val n = roundToParallelism(32000000)
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    val r = new ParRunner((1 to parallelism).map(_ => () => sendMessages(a, n / parallelism)))
    timed(n) {
      r.start()
      l.await()
    }
  }

  "Max throughput" in {
    val n = roundToParallelism(72000000)
    val l = new CountDownLatch(parallelism)
    val r = new ParRunner((1 to parallelism).map {
      _ =>
        val a = countActor(l, n / parallelism)
        () => sendMessages(a, n / parallelism)
    })
    timed(n) {
      r.start()
      l.await()
    }
  }

  "Ping latency" in {
    ping(6400000, 1)
  }

  "Ping throughput 10K" in {
    ping(8000000, 10000)
  }

  def shutdown(): Unit = fullShutdown(executorService)

  private def ping(n: Int, p: Int): Unit = {
    val l = new CountDownLatch(p * 2)
    val as = (1 to p).map {
      _ =>
        var a1: Actor[Message] = null
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

  private def blockableCountActor(l1: CountDownLatch, l2: CountDownLatch, n: Int): Actor[Message] =
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

  private def countActor(l: CountDownLatch, n: Int): Actor[Message] =
    actor[Message] {
      var i = n
      (m: Message) =>
        i -= 1
        if (i == 0) l.countDown()
    }

  private def sendMessages(a: Actor[Message], n: Int): Unit = {
    val t = Message()
    var i = n
    while (i > 0) {
      a ! t
      i -= 1
    }
  }
}
