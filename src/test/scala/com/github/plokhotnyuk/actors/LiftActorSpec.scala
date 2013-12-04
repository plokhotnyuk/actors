package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import net.liftweb.actor.{ILAExecute, LAScheduler, LiftActor}
import com.github.plokhotnyuk.actors.BenchmarkSpec._
import net.liftweb.common.Full

class LiftActorSpec extends BenchmarkSpec {
  LAScheduler.createExecutor = () => new ILAExecute {
    val executorService = createExecutorService()

    def execute(f: () => Unit): Unit =
      executorService.execute(new Runnable {
        def run(): Unit = f()
      })

    def shutdown(): Unit = fullShutdown(executorService)
  }

  "Single-producer sending" in {
    val n = 10000000
    val l = new CountDownLatch(1)
    val a = tickActor(l, n)
    timed(n) {
      sendTicks(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = 10000000
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
    val n = 16000000
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
    ping(2000000, 1)
  }

  "Ping throughput 10K" in {
    ping(5000000, 10000)
  }

  "Initiation 1M" in {
    footprintedCollect(1000000)(() => new LiftActor {
      def messageHandler = {
        case _ =>
      }
    })
  }

  def ping(n: Int, p: Int): Unit = {
    val l = new CountDownLatch(p * 2)
    val as = (1 to p).map {
      _ =>
        var a1: LiftActor = null
        val a2 = new LiftActor {
          private var i = n / p / 2

          override val highPriorityReceive = Full[PartialFunction[Any, Unit]]({
            case m =>
              if (i > 0) a1 ! m
              i -= 1
              if (i == 0) l.countDown()
          })

          def messageHandler = {
            case _ =>
          }
        }
        a1 = new LiftActor {
          private var i = n / p / 2

          override val highPriorityReceive = Full[PartialFunction[Any, Unit]]({
            case m =>
              if (i > 0) a2 ! m
              i -= 1
              if (i == 0) l.countDown()
          })

          def messageHandler = {
            case _ =>
          }
        }
        a2
    }
    timed(n) {
      as.foreach(_ ! Message())
      l.await()
    }
  }

  def shutdown(): Unit = LAScheduler.shutdown()

  private def tickActor(l: CountDownLatch, n: Int): LiftActor =
    new LiftActor {
      private var i = n

      override val highPriorityReceive = Full[PartialFunction[Any, Unit]]({
        case _ =>
          i -= 1
          if (i == 0) l.countDown()
      })

      def messageHandler = {
        case _ =>
      }
    }

  private def sendTicks(a: LiftActor, n: Int): Unit = {
    val m = Message()
    var i = n
    while (i > 0) {
      a ! m
      i -= 1
    }
  }
}
