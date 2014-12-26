package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import com.github.plokhotnyuk.actors.BenchmarkSpec._
import org.specs2.execute.Success
import scala.concurrent.ExecutionContext

class SimplisticActorSpec extends BenchmarkSpec {
  "Enqueueing" in {
    val n = 8000000
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
    val n = 8000000
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
    val executor = createExecutorService(1)
    val commonContext = ExecutionContext.fromExecutor(executor)
    footprintedAndTimedCollect(1000000)(() => new SimplisticActor[Message] {
      override implicit val context: ExecutionContext = commonContext

      override def receive: Message => Unit = (m: Message) => ()
    })
    executor.shutdown()
    Success()
  }

  "Single-producer sending" in {
    val n = 4000000
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    timed(n) {
      sendMessages(a, n)
      l.await()
    }
    Success()
  }

  "Multi-producer sending" in {
    val n = roundToParallelism(4000000)
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
    val n = roundToParallelism(8000000)
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
    ping(2000000, 1)
    Success()
  }

  "Ping throughput 10K" in {
    ping(2000000, 10000)
    Success()
  }

  def shutdown(): Unit = ()

  private def ping(n: Int, p: Int): Unit = {
    val m = parallelism / 2
    val executors = (1 to parallelism).map(_ => createExecutorService(1))
    val (contexts1, contexts2) = executors.map(ExecutionContext.fromExecutor).splitAt(m)
    val l = new CountDownLatch(p * 2)
    val as = (1 to p).map {
      _ =>
        var a1: SimplisticActor[Message] = null
        val a2 = new SimplisticActor[Message] {
          private var i = n / p / 2

          override implicit val context: ExecutionContext = contexts1(p % m)

          override def receive: (Message) => Unit = (m: Message) => {
            if (i > 0) a1 ! m
            i -= 1
            if (i == 0) l.countDown()
          }
        }
        a1 = new SimplisticActor[Message] {
          private var i = n / p / 2

          override implicit val context: ExecutionContext = contexts2(p % m)

          override def receive: (Message) => Unit = (m: Message) => {
            if (i > 0) a2 ! m
            i -= 1
            if (i == 0) l.countDown()
          }
        }
        a2
    }
    timed(n, printAvgLatency = p == 1) {
      as.foreach(_ ! Message())
      l.await()
    }
    executors.foreach(_ => shutdown())
  }

  private def blockableCountActor(l1: CountDownLatch, l2: CountDownLatch, n: Int): SimplisticActor[Message] =
    new SimplisticActor[Message] {
      private var blocked = true
      private var i = n - 1

      override def receive: (Message) => Unit = (m: Message) => {
        if (blocked) {
          l1.await()
          blocked = false
        } else {
          i -= 1
          if (i == 0) l2.countDown()
        }
      }
    }

  private def countActor(l: CountDownLatch, n: Int): SimplisticActor[Message] =
    new SimplisticActor[Message] {
      private var i = n

      override def receive: (Message) => Unit = (m: Message) => {
        i -= 1
        if (i == 0) l.countDown()
      }
    }

  private def sendMessages(a: SimplisticActor[Message], n: Int): Unit = {
    val m = Message()
    var i = n
    while (i > 0) {
      a ! m
      i -= 1
    }
  }
}
