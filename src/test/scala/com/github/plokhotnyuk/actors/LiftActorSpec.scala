package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import net.liftweb.actor.LiftActor
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._

@RunWith(classOf[JUnitRunner])
class LiftActorSpec extends Specification {
  sequential

  "Single-producer sending" in {
    val n = 20000000
    val l = new CountDownLatch(1)
    val a = tickActor(l, n)
    timed("Single-producer sending", n) {
      sendTicks(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = 20000000
    val l = new CountDownLatch(1)
    val a = tickActor(l, n)
    timed("Multi-producer sending", n) {
      for (j <- 1 to CPUs) fork {
        sendTicks(a, n / CPUs)
      }
      l.await()
    }
  }

  "Ping between actors" in {
    val n = 2000000
    val l = new CountDownLatch(2)
    var p1: LiftActor = null
    val p2 = new LiftActor {
      private[this] var i = n / 2

      def messageHandler = {
        case b =>
          p1 ! b
          i -= 1
          if (i == 0) l.countDown()
      }
    }
    p1 = new LiftActor {
      private[this] var i = n / 2

      def messageHandler = {
        case b =>
          p2 ! b
          i -= 1
          if (i == 0) l.countDown()
      }
    }
    timed("Ping between actors", n) {
      p2 ! Message()
      l.await()
    }
  }

  "Single-producer asking" in {
    val n = 1000000
    val a = echoActor
    timed("Single-producer asking", n) {
      requestEchos(a, n)
    }
  }

  "Multi-producer asking" in {
    val n = 2000000
    val l = new CountDownLatch(CPUs)
    val a = echoActor
    timed("Multi-producer asking", n) {
      for (j <- 1 to CPUs) fork {
        requestEchos(a, n / CPUs)
        l.countDown()
      }
      l.await()
    }
  }

  "Max throughput" in {
    val n = 20000000
    val l = new CountDownLatch(halfOfCPUs)
    val as = for (j <- 1 to halfOfCPUs) yield tickActor(l, n / halfOfCPUs)
    timed("Max throughput", n) {
      for (a <- as) fork {
        sendTicks(a, n / halfOfCPUs)
      }
      l.await()
    }
  }

  private[this] def tickActor(l: CountDownLatch, n: Int): LiftActor =
    new LiftActor {
      private[this] var i = n

      def messageHandler = {
        case _ =>
          i -= 1
          if (i == 0) {
            l.countDown()
          }
      }
    }

  private[this] def sendTicks(a: LiftActor, n: Int) {
    val m = Message()
    var i = n
    while (i > 0) {
      a ! m
      i -= 1
    }
  }

  private[this] def echoActor: LiftActor = new LiftActor {
    def messageHandler = {
      case m => reply(m)
    }
  }

  private[this] def requestEchos(a: LiftActor, n: Int) {
    val m = Message()
    var i = n
    while (i > 0) {
      a !? m
      i -= 1
    }
  }
}