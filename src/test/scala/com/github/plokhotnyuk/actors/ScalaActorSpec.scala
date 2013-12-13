package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
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

  "Single-producer sending" in {
    val n = 1200000
    val l = new CountDownLatch(1)
    val a = tickActor(l, n)
    timed(n) {
      sendTicks(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = roundToParallelism(1200000)
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
    val n = roundToParallelism(3000000)
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
    ping(600000, 1)
  }

  "Ping throughput 10K" in {
    ping(2000000, 10000)
  }

  "Initiation 1M" in {
    footprintedCollect(1000000)(() => new Actor {
      def act(): Unit =
        loop {
          react {
            case _ =>
          }
        }

      override def scheduler = customScheduler
    })
  }

  def ping(n: Int, p: Int): Unit = {
    val l = new CountDownLatch(p * 2)
    val as = (1 to p).map(_ => (playerActor(l, n / p / 2), playerActor(l, n / p / 2)))
    timed(n, printAvgLatency = p == 1) {
      as.foreach {
        case (a1, a2) => a1.send(Message(), a2)
      }
      l.await()
    }
  }

  def shutdown(): Unit = customScheduler.shutdown()

  private def tickActor(l: CountDownLatch, n: Int): Actor =
    new Actor {
      private var i = n

      def act(): Unit =
        loop {
          react {
            case _ =>
              i -= 1
              if (i == 0) {
                l.countDown()
                exit()
              }
          }
        }

      override def scheduler = customScheduler
    }.start()

  private def sendTicks(a: Actor, n: Int): Unit = {
    val m = Message()
    var i = n
    while (i > 0) {
      a ! m
      i -= 1
    }
  }

  private def playerActor(l: CountDownLatch, n: Int): Actor =
    new Actor {
      private var i = n

      def act(): Unit =
        loop {
          react {
            case m =>
              if (i > 0) sender ! m
              i -= 1
              if (i == 0) {
                l.countDown()
                exit()
              }
          }
        }

      override def scheduler = customScheduler
    }.start()
}
