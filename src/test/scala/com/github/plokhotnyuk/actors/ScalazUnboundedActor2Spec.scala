package com.github.plokhotnyuk.actors

import com.github.plokhotnyuk.actors.Actor2._
import java.util.concurrent.CountDownLatch
import com.github.plokhotnyuk.actors.BenchmarkSpec._
import org.specs2.execute.Success
import scalaz.concurrent.Strategy

class ScalazUnboundedActor2Spec extends BenchmarkSpec {
  val executorService = createExecutorService()
  implicit val strategy = Strategy.Executor(executorService)

  "Enqueueing" in {
    val n = 40000000
    val l1 = new CountDownLatch(1)
    val l2 = new CountDownLatch(1)
    val a = blockableCountActor(l1, l2, n)
    footprintedAndTimed(n) {
      sendMessages(a, n)
    }
    l1.countDown()
    l2.await()
    Success()
  }

  "Dequeueing" in {
    val n = 40000000
    val l1 = new CountDownLatch(1)
    val l2 = new CountDownLatch(1)
    val a = blockableCountActor(l1, l2, n)
    sendMessages(a, n)
    timed(n) {
      l1.countDown()
      l2.await()
    }
    Success()
  }

  "Initiation" in {
    footprintedAndTimedCollect(10000000)(() => actor((_: Message) => ()))
    Success()
  }

  "Single-producer sending" in {
    val n = 16000000
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    timed(n) {
      sendMessages(a, n)
      l.await()
    }
    Success()
  }

  "Multi-producer sending" in {
    val n = roundToParallelism(16000000)
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    val r = new ParRunner((1 to parallelism).map(_ => () => sendMessages(a, n / parallelism)))
    timed(n) {
      r.start()
      l.await()
    }
    Success()
  }

  "Max throughput" in {
    val n = roundToParallelism(32000000)
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
    Success()
  }

  "Ping latency" in {
    ping(3200000, 1)
    Success()
  }

  "Ping throughput 10K" in {
    ping(4000000, 10000)
    Success()
  }

  def shutdown(): Unit = fullShutdown(executorService)

  def actor[A](handler: A => Unit): Actor2[A] = unboundedActor(handler)

  private def ping(n: Int, p: Int): Unit = {
    val l = new CountDownLatch(p * 2)
    val as = (1 to p).map {
      _ =>
        var a1: Actor2[Message] = null
        val a2 = actor {
          var i = n / p / 2
          (m: Message) =>
            if (i > 0) a1 ! m
            i -= 1
            if (i == 0) l.countDown()
        }
        a1 = actor {
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

  private def blockableCountActor(l1: CountDownLatch, l2: CountDownLatch, n: Int): Actor2[Message] =
    actor {
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
    actor {
      var i = n
      (m: Message) =>
        i -= 1
        if (i == 0) l.countDown()
    }

  protected def sendMessages(a: Actor2[Message], n: Int): Unit = {
    val t = Message()
    var i = n
    while (i > 0) {
      a ! t
      i -= 1
    }
  }
}
