package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import net.liftweb.actor.LiftActor
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._

@RunWith(classOf[JUnitRunner])
class LiftActorTest extends Specification {
  "Single-producer sending" in {
    val n = 20000000
    timed("Single-producer sending", n) {
      val l = new CountDownLatch(1)
      val a = tickActor(l, n)
      sendTicks(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = 20000000
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
    val n = 2000000
    timed("Ping between actors", n) {
      val l = new CountDownLatch(1)
      var p1: LiftActor = null
      val p2 = new LiftActor {
        def messageHandler = {
          case Ball(0) => l.countDown()
          case Ball(i) => p1 ! Ball(i - 1)
        }
      }
      p1 = new LiftActor {
        def messageHandler = {
          case Ball(0) => l.countDown()
          case Ball(i) => p2 ! Ball(i - 1)
        }
      }
      p2 ! Ball(n)
      l.await()
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
    val n = 2000000
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
    val n = 20000000
    timed("Max throughput", n) {
      val l = new CountDownLatch(halfOfCPUs)
      for (j <- 1 to halfOfCPUs) fork {
        val a = tickActor(l, n / halfOfCPUs)
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
    val t = Tick()
    var i = n
    while (i > 0) {
      a ! t
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