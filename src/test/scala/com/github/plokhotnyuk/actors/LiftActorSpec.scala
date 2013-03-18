package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import net.liftweb.actor.{ILAExecute, LAScheduler, LiftActor}

class LiftActorSpec extends BenchmarkSpec {
  LAScheduler.createExecutor = () => new ILAExecute {
    val executorService = lifoForkJoinPool(CPUs)

    def execute(f: () => Unit) {
      executorService.execute(new Runnable {
        def run() {
          f()
        }
      })
    }

    def shutdown() {
      executorService.shutdown()
    }
  }

  "Single-producer sending" in {
    val n = 20000000
    val l = new CountDownLatch(1)
    val a = tickActor(l, n)
    timed(n) {
      sendTicks(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = 20000000
    val l = new CountDownLatch(1)
    val a = tickActor(l, n)
    timed(n) {
      for (j <- 1 to CPUs) fork {
        sendTicks(a, n / CPUs)
      }
      l.await()
    }
  }

  "Max throughput" in {
    val n = 20000000
    val l = new CountDownLatch(CPUs)
    val as = for (j <- 1 to CPUs) yield tickActor(l, n / CPUs)
    timed(n) {
      for (a <- as) fork {
        sendTicks(a, n / CPUs)
      }
      l.await()
    }
  }

  "Ping between actors" in {
    val n = 2000000
    val l = new CountDownLatch(2)
    var a1: LiftActor = null
    val a2 = new LiftActor {
      private var i = n / 2

      def messageHandler = {
        case b =>
          a1 ! b
          i -= 1
          if (i == 0) l.countDown()
      }
    }
    a1 = new LiftActor {
      private var i = n / 2

      def messageHandler = {
        case b =>
          a2 ! b
          i -= 1
          if (i == 0) l.countDown()
      }
    }
    timed(n) {
      a2 ! Message()
      l.await()
    }
    a1 = null
  }

  private def tickActor(l: CountDownLatch, n: Int): LiftActor =
    new LiftActor {
      private var i = n

      def messageHandler = {
        case _ =>
          i -= 1
          if (i == 0) l.countDown()
      }
    }

  override def shutdown() {
    LAScheduler.shutdown()
  }

  private def sendTicks(a: LiftActor, n: Int) {
    val m = Message()
    var i = n
    while (i > 0) {
      a ! m
      i -= 1
    }
  }
}