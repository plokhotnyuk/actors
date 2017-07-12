package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import impromptu._
import com.github.plokhotnyuk.actors.BenchmarkSpec._
import scala.concurrent.ExecutionContext

class ImpromptuActorSpec extends BenchmarkSpec {
  private implicit val executorContext =  ExecutionContext.fromExecutorService(createExecutorService())

  "Enqueueing" in {
    val n = 400000
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
    val n = 100000
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
    footprintedAndTimedCollect(2000000)({
      val handler = (_: Unit) => handle(on[Message]((m: Message) => ()))
      () => Actor[Message, Unit](())(handler)
    })
  }

  "Single-producer sending" in {
    val n = 100000
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    timed(n) {
      sendMessages(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = roundToParallelism(100000)
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    val r = new ParRunner((1 to parallelism).map(_ => () => sendMessages(a, n / parallelism)))
    timed(n) {
      r.start()
      l.await()
    }
  }

  "Max throughput" in {
    val n = roundToParallelism(40000)
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
    pingLatency(100000)
  }

  "Ping throughput 10K" in {
    pingThroughput(60000, 10000)
  }

  def shutdown(): Unit = fullShutdown(executorContext)

  private def pingLatency(n: Int): Unit =
    latencyTimed(n) {
      h =>
        val l = new CountDownLatch(2)
        var a1: Actor[Int, Message] = null
        val a2 = Actor(n / 2)((i: Int) => handle {
          on[Message] { (m: Message) =>
            h.record()
            if (i > 0) a1.send(m)
            if (i == 1) l.countDown()
            i - 1
          }
        })
        a1 = Actor(n / 2)((i: Int) => handle {
          on[Message] { (m: Message) =>
            h.record()
            if (i > 0) a2.send(m)
            if (i == 1) l.countDown()
            i - 1
          }
        })
        a2.send(Message())
        l.await()
    }

  private def pingThroughput(n: Int, p: Int): Unit = {
    val l = new CountDownLatch(p * 2)
    val as = (1 to p).map {
      _ =>
        var a1: Actor[Int, Message] = null
        val a2 = Actor(n / p / 2)((i: Int) => handle {
          on[Message] { (m: Message) =>
            if (i > 0) a1.send(m)
            if (i == 1) l.countDown()
            i - 1
          }
        })
        a1 = Actor(n / p / 2)((i: Int) => handle {
          on[Message] { (m: Message) =>
            if (i > 0) a2.send(m)
            if (i == 1) l.countDown()
            i - 1
          }
        })
        a2
    }
    timed(n) {
      as.foreach(_.send(Message()))
      l.await()
    }
  }

  private def blockableCountActor(l1: CountDownLatch, l2: CountDownLatch, n: Int): Actor[Int, Message] =
    Actor(n)((i: Int) => handle {
      on[Message] { _ =>
          val blocked = i == n
          if (blocked) l1.await()
          else if (i == 1) l2.countDown()
          i - 1
      }
    })

  private def countActor(l: CountDownLatch, n: Int): Actor[Int, Message] =
    Actor(n)((i: Int) => handle {
      on[Message] { _ =>
        if (i == 1) l.countDown()
        i - 1
      }
    })

  protected def sendMessages(a: Actor[Int, Message], n: Int): Unit = {
    val m = Message()
    var i = n
    while (i > 0) {
      a.send(m)
      i -= 1
    }
  }
}