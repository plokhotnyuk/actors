package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import com.github.gist.viktorklang.Actor
import com.github.gist.viktorklang.Actor._
import com.github.plokhotnyuk.actors.BenchmarkSpec._

class MinimalistActorSpec extends BenchmarkSpec {
  private implicit val executorService = createExecutorService()

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
  }

  "Initiation" in {
    val es = createExecutorService()
    footprintedAndTimedCollect(2000000)({
      val f = (_: Address) => (_: Any) => Stay
      () => Actor(f)(es)
    }, fullShutdown(es))
  }

  "Single-producer sending" in {
    val n = 15000000
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    timed(n) {
      sendMessages(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = roundToParallelism(15000000)
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    val r = new ParRunner((1 to parallelism).map(_ => () => sendMessages(a, n / parallelism)))
    timed(n) {
      r.start()
      l.await()
    }
  }

  "Max throughput" in {
    val n = roundToParallelism(30000000)
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
    pingLatency(3000000)
  }

  "Ping throughput 10K" in {
    pingThroughput(6000000, 10000)
  }

  def shutdown(): Unit = fullShutdown(executorService)

  private def pingLatency(n: Int): Unit =
    latencyTimed(n) {
      h =>
        val l = new CountDownLatch(2)
        var a1: Address = null
        val a2 = Actor(_ => {
          var i = n / 2
          (m: Any) =>
            h.record()
            if (i > 0) a1 ! m
            i -= 1
            if (i == 0) l.countDown()
            Stay
        }, batch = 1024)
        a1 = Actor(_ => {
          var i = n / 2
          (m: Any) =>
            h.record()
            if (i > 0) a2 ! m
            i -= 1
            if (i == 0) l.countDown()
            Stay
        }, batch = 1024)
        a2 ! Message()
        l.await()
    }

  private def pingThroughput(n: Int, p: Int): Unit = {
    val l = new CountDownLatch(p * 2)
    val as = (1 to p).map {
      _ =>
        var a1: Address = null
        val a2 = Actor(_ => {
          var i = n / p / 2
          (m: Any) =>
            if (i > 0) a1 ! m
            i -= 1
            if (i == 0) l.countDown()
            Stay
        }, batch = 1024)
        a1 = Actor(_ => {
          var i = n / p / 2
          (m: Any) =>
            if (i > 0) a2 ! m
            i -= 1
            if (i == 0) l.countDown()
            Stay
        }, batch = 1024)
        a2
    }
    timed(n) {
      as.foreach(_ ! Message())
      l.await()
    }
  }

  private def blockableCountActor(l1: CountDownLatch, l2: CountDownLatch, n: Int): Address =
    Actor(_ => {
      var blocked = true
      var i = n - 1
      (_: Any) =>
        if (blocked) {
          l1.await()
          blocked = false
        } else {
          i -= 1
          if (i == 0) l2.countDown()
        }
        Stay
    }, batch = 1024)

  private def countActor(l: CountDownLatch, n: Int): Address =
    Actor(_ => {
      var i = n
      (_: Any) =>
        i -= 1
        if (i == 0) l.countDown()
        Stay
    }, batch = 1024)

  protected def sendMessages(a: Address, n: Int): Unit = {
    val m = Message()
    var i = n
    while (i > 0) {
      a ! m
      i -= 1
    }
  }
}