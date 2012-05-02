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
    var ping: Actor2[Int] = null
    var pong: Actor2[Int] = null
    ping = actor2[Int](
      (b: Int) => b match {
        case 0 => gameOver.countDown()
        case i => pong ! i - 1
      }
    )
    pong = actor2[Int](
      (b: Int) => b match {
        case 0 => gameOver.countDown()
        case i => ping ! i - 1
      }
    )
    val n = 20000000
    timed("Ping between actors", n) {
      ping ! n
      gameOver.await()
    }
    executor.shutdown()
    executor.awaitTermination(10L, TimeUnit.SECONDS)
  }
}