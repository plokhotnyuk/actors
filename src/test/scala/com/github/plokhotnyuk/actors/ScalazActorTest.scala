package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._
import scalaz._
import concurrent.Strategy
import Scalaz._
import java.util.concurrent.{TimeUnit, CountDownLatch}
import akka.jsr166y.ForkJoinPool

@RunWith(classOf[JUnitRunner])
class ScalazActorTest extends Specification with AvailableProcessorsParallelism {

  "Single-producer sending" in {
    val n = 40000000
    val bang = new CountDownLatch(1)

    var countdown = n
    implicit val executor = new ForkJoinPool()
    import Strategy.Executor
    val countdownActor = actor[Tick] {
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
    val countdownActor = actor[Tick] {
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

    val player =
      (b: BallZ) => b match {
        case BallZ(0, _, _) => gameOver.countDown()
        case BallZ(i, p1, p2) => p1 ! BallZ(i - 1, p2, p1)
      }

    implicit val executor = new ForkJoinPool()
    import Strategy.Executor
    val ping = actor[BallZ](player)
    val pong = actor[BallZ](player)
    val n = 20000000
    timed("Ping between actors", n) {
      ping ! BallZ(n, pong, ping)
      gameOver.await()
    }
    executor.shutdown()
    executor.awaitTermination(10L, TimeUnit.SECONDS)
  }
}