package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._
import java.util.concurrent.CountDownLatch
import akka.jsr166y.ForkJoinPool
import Scalaz2._
import scalaz.concurrent.Strategy

@RunWith(classOf[JUnitRunner])
class ScalazActor2Test extends Specification {
  implicit val executor = new ForkJoinPool(availableProcessors)
  import Strategy.Executor

  "Single-producer sending" in {
    val n = 40000000
    timed("Single-producer sending", n) {
      val bang = new CountDownLatch(1)
      var countdown = n
      val countdownActor = actor2[Tick] {
        (t: Tick) =>
          countdown -= 1
          if (countdown == 0) {
            bang.countDown()
          }
      }
      val tick = Tick()
      var i = n
      while (i > 0) {
        countdownActor ! tick
        i -= 1
      }
      bang.await()
    }
  }

  "Multi-producer sending" in {
    val n = 40000000
    timed("Multi-producer sending", n) {
      val bang = new CountDownLatch(1)
      var countdown = n
      val countdownActor = actor2[Tick] {
        (t: Tick) =>
          countdown -= 1
          if (countdown == 0) {
            bang.countDown()
          }
      }
      val p = availableProcessors
      for (j <- 1 to p) {
        fork {
          val tick = Tick()
          val countdown = countdownActor
          var i = n / p
          while (i > 0) {
            countdown ! tick
            i -= 1
          }
        }
      }
      bang.await()
    }
  }

  "Ping between actors" in {
    val n = 20000000
    timed("Ping between actors", n) {
      val gameOver = new CountDownLatch(1)
      var pong: Actor2[Ball] = null
      val ping = actor2[Ball](
        (b: Ball) => b match {
          case Ball(0) => gameOver.countDown()
          case Ball(i) => pong ! Ball(i - 1)
        }
      )
      pong = actor2[Ball](
        (b: Ball) => b match {
          case Ball(0) => gameOver.countDown()
          case Ball(i) => ping ! Ball(i - 1)
        }
      )
      ping ! Ball(n)
      gameOver.await()
    }
  }

  "Max throughput" in {
    val n = 40000000
    timed("Max throughput", n) {
      val p = availableProcessors / 2
      val bang = new CountDownLatch(p)
      for (j <- 1 to p) {
        fork {
          var countdown = n / p
          val countdownActor = actor2[Tick] {
            (t: Tick) =>
              countdown -= 1
              if (countdown == 0) {
                bang.countDown()
              }
          }
          val tick = Tick()
          var i = n
          while (i > 0) {
            countdownActor ! tick
            i -= 1
          }
        }
      }
      bang.await()
    }
  }
}