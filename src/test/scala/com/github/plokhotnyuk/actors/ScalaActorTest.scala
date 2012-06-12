package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._
import actors.{Exit, Actor}

@RunWith(classOf[JUnitRunner])
class ScalaActorTest extends Specification {
  "Single-producer sending" in {
    val n = 1000000
    timed("Single-producer sending", n) {
      val l = new CountDownLatch(1)
      val a = tickActor(l, n)
      sendTicks(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = 1000000
    timed("Multi-producer sending", n) {
      val l = new CountDownLatch(1)
      val a = tickActor(l, n)
      for (j <- 1 to CPUs) fork {
        sendTicks(a, n / CPUs)
      }
      l.await()
    }
  }

  "Ping between actors" in {
    val n = 1000000
    timed("Ping between actors", n) {
      val l = new CountDownLatch(2)
      val p1 = playerActor(l, n / 2)
      val p2 = playerActor(l, n / 2)
      p1.send(Message(), p2)
      l.await()
      exit(p1)
      exit(p2)
    }
  }

  "Single-producer asking" in {
    val n = 1000000
    timed("Single-producer asking", n) {
      val a = echoActor
      requestEchos(a, n)
    }
  }

  "Multi-producer asking" in {
    val n = 1000000
    timed("Multi-producer asking", n) {
      val l = new CountDownLatch(CPUs)
      val a = echoActor
      for (j <- 1 to CPUs) fork {
        requestEchos(a, n / CPUs)
        l.countDown()
      }
      l.await()
    }
  }

  "Max throughput" in {
    val n = 1000000
    timed("Max throughput", n) {
      val l = new CountDownLatch(halfOfCPUs)
      for (j <- 1 to halfOfCPUs) fork {
        val a = tickActor(l, n / halfOfCPUs)
        sendTicks(a, n / halfOfCPUs)
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
    exit(a)
  }

  private[this] def exit(a: Actor) {
    a ! Exit(null, null)
  }
}