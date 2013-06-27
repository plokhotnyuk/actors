package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import scala.actors.{SchedulerAdapter, Actor}
import com.github.plokhotnyuk.actors.BenchmarkSpec._

class ScalaActorSpec extends BenchmarkSpec {
  val customScheduler = new SchedulerAdapter {
    val executorService = createExecutorService()

    def execute(fun: => Unit) {
      executorService.execute(new Runnable {
        def run() {
          fun
        }
      })
    }

    override def executeFromActor(task: Runnable) {
      executorService.execute(task)
    }

    override def execute(task: Runnable) {
      executorService.execute(task)
    }

    override def shutdown() {
      fullShutdown(executorService)
    }

    override def isActive: Boolean = !executorService.isShutdown
  }

  "Single-producer sending" in {
    val n = 1000000
    val l = new CountDownLatch(1)
    val a = tickActor(l, n)
    timed(n) {
      sendTicks(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = 1000000
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
    val n = 5000000
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
    val n = 1000000
    val l = new CountDownLatch(2)
    val a1 = playerActor(l, n / 2)
    val a2 = playerActor(l, n / 2)
    timed(n) {
      a1.send(Message(), a2)
      l.await()
    }
  }

  "Ping throughput" in {
    val p = 1000
    val n = 2000000
    val l = new CountDownLatch(p * 2)
    val as = for (i <- 1 to p) yield (playerActor(l, n / p / 2), playerActor(l, n / p / 2))
    timed(n) {
      as.foreach {
        case (a1, a2) => a1.send(Message(), a2)
      }
      l.await()
    }
  }

  def shutdown() {
    customScheduler.shutdown()
  }

  private def tickActor(l: CountDownLatch, n: Int): Actor =
    new Actor {
      private var i = n

      def act() {
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
      }

      override def scheduler = customScheduler
    }.start()

  private def sendTicks(a: Actor, n: Int) {
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

      def act() {
        loop {
          react {
            case m =>
              sender ! m
              i -= 1
              if (i == 0) {
                l.countDown()
                exit()
              }
          }
        }
      }

      override def scheduler = customScheduler
    }.start()
}