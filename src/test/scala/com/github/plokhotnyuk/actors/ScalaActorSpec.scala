package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import org.specs2.execute.Success

import scala.actors.{SchedulerAdapter, Actor}
import com.github.plokhotnyuk.actors.BenchmarkSpec._

class ScalaActorSpec extends BenchmarkSpec {
  val customScheduler = new SchedulerAdapter {
    val executorService = createExecutorService()

    def execute(f: => Unit): Unit =
      executorService.execute(new Runnable {
        def run(): Unit = f
      })

    override def executeFromActor(task: Runnable): Unit = executorService.execute(task)

    override def execute(task: Runnable): Unit = executorService.execute(task)

    override def shutdown(): Unit = fullShutdown(executorService)

    override def isActive: Boolean = !executorService.isShutdown
  }

  "Enqueueing" in {
    val n = 40000000
    val l1 = new CountDownLatch(1)
    val l2 = new CountDownLatch(1)
    val a = blockableCountActor(l1, l2, 2) // hack to exit without dequeueing
    footprintedAndTimed(n) {
      sendMessages(a, n)
    }
    l1.countDown()
    l2.await()
    Success()
  }

  "Dequeueing" in {
    val n = 600000
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
    footprintedAndTimedCollect(600000)(() => new Actor {
      def act(): Unit = loop {
        react {
          case _ =>
        }
      }

      override def scheduler = customScheduler
    }.start())
    Success()
  }

  "Single-producer sending" in {
    val n = 300000
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    timed(n) {
      sendMessages(a, n)
      l.await()
    }
    Success()
  }

  "Multi-producer sending" in {
    val n = roundToParallelism(300000)
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
    val n = roundToParallelism(600000)
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
    ping(250000, 1)
    Success()
  }

  "Ping throughput 10K" in {
    ping(400000, 10000)
    Success()
  }

  def shutdown(): Unit = customScheduler.shutdown()

  private def ping(n: Int, p: Int): Unit = {
    val l = new CountDownLatch(p * 2)
    val as = (1 to p).map(_ => (replayAndCountActor(l, n / p / 2), replayAndCountActor(l, n / p / 2)))
    timed(n, printAvgLatency = p == 1) {
      as.foreach {
        case (a1, a2) => a1.send(Message(), a2)
      }
      l.await()
    }
  }

  private def replayAndCountActor(l: CountDownLatch, n: Int): Actor =
    new Actor {
      private var i = n

      def act(): Unit =
        loop(react {
          case m =>
            if (i > 0) sender ! m
            i -= 1
            if (i == 0) {
              l.countDown()
              exit()
            }
        })

      override def scheduler = customScheduler
    }.start()

  private def blockableCountActor(l1: CountDownLatch, l2: CountDownLatch, n: Int): Actor =
    new Actor {
      private var blocked = true
      private var i = n - 1

      def act(): Unit =
        loop(react {
          case _ =>
            if (blocked) {
              l1.await()
              blocked = false
            } else {
              i -= 1
              if (i == 0) {
                l2.countDown()
                exit()
              }
            }
        })

      override def scheduler = customScheduler
    }.start()

  private def countActor(l: CountDownLatch, n: Int): Actor =
    new Actor {
      private var i = n

      def act(): Unit =
        loop(react {
          case _ =>
            i -= 1
            if (i == 0) {
              l.countDown()
              exit()
            }
        })

      override def scheduler = customScheduler
    }.start()

  private def sendMessages(a: Actor, n: Int): Unit = {
    val m = Message()
    var i = n
    while (i > 0) {
      a ! m
      i -= 1
    }
  }
}
