package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._
import java.util.concurrent.{TimeUnit, CountDownLatch}
import akka.jsr166y.ForkJoinPool
import Scalaz2._
import scalaz.concurrent.Strategy

@RunWith(classOf[JUnitRunner])
class ScalazActor2Test extends Specification with AvailableProcessorsParallelism {

  "Single-producer sending" in {
    val n = 100000000
    val bang = new CountDownLatch(1)

    var countdown = n
    implicit val executor = new ForkJoinPool()
    import Strategy.Executor
    val countdownActor = actor2[Tick] {
      (t: Tick) =>
        countdown -= 1
        if (countdown == 0) {
          bang.countDown()
        }
    }

    timed("Single-producer sending", n) {
      val countdown = countdownActor
      val tick = Tick()
      var i = n
      while (i > 0) {
        countdown ! tick
        i -= 1
      }
      bang.await()
    }
    executor.shutdown()
    executor.awaitTermination(10L, TimeUnit.SECONDS)
  }

  "Multi-producer sending" in {
    val n = 40000000
    val bang = new CountDownLatch(1)

    var countdown = n
    implicit val executor = new ForkJoinPool()
    import Strategy.Executor
    val countdownActor = actor2[Tick] {
      (t: Tick) =>
        countdown -= 1
        if (countdown == 0) {
          bang.countDown()
        }
    }

    timed("Multi-producer sending", n) {
      val countdown = countdownActor
      val tick = Tick()
      (1 to n).par.foreach(i => countdown ! tick)
      bang.await()
    }
    executor.shutdown()
    executor.awaitTermination(10L, TimeUnit.SECONDS)
  }

  "Ping between actors" in {
    val gameOver = new CountDownLatch(1)
    implicit val executor = new ForkJoinPool()
    import Strategy.Executor
    var ping: Actor2[Ball] = null
    var pong: Actor2[Ball] = null
    ping = actor2[Ball](
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
    val n = 20000000
    timed("Ping between actors", n) {
      ping ! Ball(n)
      gameOver.await()
    }
    executor.shutdown()
    executor.awaitTermination(10L, TimeUnit.SECONDS)
  }

  "Max throughput" in {
    val n = 100000000
    val p = availableProcessors / 2
    val bang = new CountDownLatch(p)

    implicit val executor = new ForkJoinPool()
    import Strategy.Executor
    timed("Max throughput", n) {
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
    executor.shutdown()
    executor.awaitTermination(10L, TimeUnit.SECONDS)
  }
}