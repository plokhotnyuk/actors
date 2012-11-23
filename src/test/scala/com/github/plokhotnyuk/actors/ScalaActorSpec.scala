package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import actors.{Exit, Actor}

class ScalaActorSpec extends BenchmarkSpec {
  "Single-producer sending" in {
    val n = 200000
    val l = new CountDownLatch(1)
    val a = tickActor(l, n)
    timed(n) {
      sendTicks(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = 200000
    val l = new CountDownLatch(1)
    val a = tickActor(l, n)
    timed(n) {
      for (j <- 1 to CPUs) fork {
        sendTicks(a, n / CPUs)
      }
      l.await()
    }
  }

  "Ping between actors" in {
    val n = 200000
    val l = new CountDownLatch(2)
    val p1 = playerActor(l, n / 2)
    val p2 = playerActor(l, n / 2)
    timed(n) {
      p1.send(Message(), p2)
      l.await()
      p1 ! Exit(null, null)
      p2 ! Exit(null, null)
    }
  }

  "Single-producer asking" in {
    val n = 100000
    timed(n) {
      val a = echoActor
      requestEchos(a, n)
    }
  }

  "Multi-producer asking" in {
    val n = 200000
    val l = new CountDownLatch(CPUs)
    val a = echoActor
    timed(n) {
      for (j <- 1 to CPUs) fork {
        requestEchos(a, n / CPUs)
        l.countDown()
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

  private[this] def tickActor(l: CountDownLatch, n: Int): Actor = {
    val a = new Actor {
      private[this] var i = n

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

  private[this] def sendTicks(a: Actor, n: Int) {
    val m = Message()
    var i = n
    while (i > 0) {
      a ! m
      i -= 1
    }
  }

  private[this] def playerActor(l: CountDownLatch, n: Int): Actor = {
    val a = new Actor {
      private[this] var i = n

      def act() {
        loop {
          react {
            case m =>
              sender ! m
              i -= 1
              if (i == 0) l.countDown()
          }
        }
      }
    }
    a.start()
  }

  private[this] def echoActor: Actor = {
    val a = new Actor {
      def act() {
        loop {
          react {
            case m => sender ! m
          }
        }
      }
    }
    a.start()
    a
  }

  private[this] def requestEchos(a: Actor, n: Int) {
    val m = Message()
    var i = n
    while (i > 0) {
      a !? m
      i -= 1
    }
    a ! Exit(null, null)
  }
}