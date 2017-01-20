package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import com.github.plokhotnyuk.actors.BenchmarkSpec._
import org.specs2.execute.Success
import rapture.core.{Actor, ActorResponse, Ignore, Transition, Update}
import scala.concurrent.ExecutionContext

class RaptorActorSpec extends BenchmarkSpec {
  val executorService = createExecutorService()
  implicit val executionContext = ExecutionContext.fromExecutorService(executorService)

  "Enqueueing" in {
    val n = 4000000
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
    val n = 1000000
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
    val es = createExecutorService()
    val ec = ExecutionContext.fromExecutorService(es)
    footprintedAndTimedCollect(2000000)({
      val f: Transition[Message, Unit] => ActorResponse[Unit, Unit] = _ => Ignore
      () => Actor.of(())(f)(ec)
    }, fullShutdown(es))
    Success()
  }

  "Single-producer sending" in {
    val n = 1500000
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    timed(n) {
      sendMessages(a, n)
      l.await()
    }
    Success()
  }
/* hangs due to concurrency error
  "Multi-producer sending" in {
    val n = roundToParallelism(1000000)
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    val r = new ParRunner((1 to parallelism).map(_ => () => sendMessages(a, n / parallelism)))
    timed(n) {
      r.start()
      l.await()
    }
    Success()
  }*/

  "Max throughput" in {
    val n = roundToParallelism(2000000)
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
    pingLatency(2000000)
    Success()
  }

  "Ping throughput 10K" in {
    pingThroughput(2000000, 10000)
    Success()
  }

  def shutdown(): Unit = fullShutdown(executorService)

  private def pingLatency(n: Int): Unit =
    latencyTimed(n) {
      h =>
        val l = new CountDownLatch(2)
        var a1: Actor[Message, Unit, Int] = null
        val a2: Actor[Message, Unit, Int] = Actor.of(n / 2) { case Transition(m, i) =>
          h.record()
          if (i > 0) a1.cue(m)
          if (i == 1) l.countDown()
          Update((), i - 1)
        }
        a1 = Actor.of(n / 2) { case Transition(m, i) =>
          h.record()
          if (i > 0) a2.cue(m)
          if (i == 1) l.countDown()
          Update((), i - 1)
        }
        a2.cue(Message())
        l.await()
    }

  private def pingThroughput(n: Int, p: Int): Unit = {
    val l = new CountDownLatch(p * 2)
    val as = (1 to p).map {
      _ =>
        var a1: Actor[Message, Unit, Int] = null
        val a2: Actor[Message, Unit, Int] = Actor.of(n / p / 2) { case Transition(m, i) =>
          if (i > 0) a1 cue m
          if (i == 1) l.countDown()
          Update((), i - 1)
        }
        a1 = Actor.of(n / p / 2) { case Transition(m, i) =>
          if (i > 0) a2.cue(m)
          if (i == 1) l.countDown()
          Update((), i - 1)
        }
        a2
    }
    timed(n) {
      as.foreach(_ cue Message())
      l.await()
    }
  }

  private def blockableCountActor(l1: CountDownLatch, l2: CountDownLatch, n: Int): Actor[Message, Unit, Tuple2[Boolean, Int]] =
    Actor.of((true, n - 1)) { case Transition(_, (blocked, i)) =>
      if (blocked) {
        l1.await()
        Update((), (false, i))
      } else {
        if (i == 1) l2.countDown()
        Update((), (blocked, i - 1))
      }
    }

  private def countActor(l: CountDownLatch, n: Int): Actor[Message, Unit, Int] =
    Actor.of(n) { case Transition(_, i) =>
      if (i == 1) l.countDown()
      Update((), i - 1)
    }

  protected def sendMessages[T](a: Actor[Message, Unit, T], n: Int): Unit = {
    val m = Message()
    var i = n
    while (i > 0) {
      a.cue(m)
      i -= 1
    }
  }
}