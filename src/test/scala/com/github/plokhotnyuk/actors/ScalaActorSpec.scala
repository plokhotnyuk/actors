package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import actors.Actor
import com.github.plokhotnyuk.actors.BenchmarkSpec._

class ScalaActorSpec extends BenchmarkSpec {
  System.setProperty("actors.corePoolSize", CPUs.toString)
  System.setProperty("actors.maxPoolSize", CPUs.toString)
  System.setProperty("actors.enableForkJoin", "true")

  "Single-producer sending" in {
    val n = 500000
    val l = new CountDownLatch(1)
    val a = tickActor(l, n)
    timed(n) {
      sendTicks(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = 500000
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
    val n = 5000000
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
    val n = 500000
    val l = new CountDownLatch(2)
    val p1 = playerActor(l, n / 2)
    val p2 = playerActor(l, n / 2)
    timed(n) {
      p1.send(Message(), p2)
      l.await()
    }
  }

  private def tickActor(l: CountDownLatch, n: Int): Actor = {
    val a = new Actor {
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
    }
    a.start()
    a
  }

  private def sendTicks(a: Actor, n: Int) {
    val m = Message()
    var i = n
    while (i > 0) {
      a ! m
      i -= 1
    }
  }

  private def playerActor(l: CountDownLatch, n: Int): Actor = {
    val a = new Actor {
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
    }
    a.start()
  }
}