package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._
import scalaz._
import concurrent.Strategy
import Scalaz._
import java.util.concurrent.CountDownLatch
import scala.concurrent.forkjoin.ForkJoinPool

@RunWith(classOf[JUnitRunner])
class ScalazActorTest extends Specification with AvailableProcessorsParallelism {

  "Single-producer sending" in {
    val n = 40000000
    val bang = new CountDownLatch(1)

    var countdown = n
    val countdownActor = actor[Tick] {
      (t: Tick) =>
        countdown -= 1
        if (countdown == 0) {
          bang.countDown()
        }
    }

    implicit val pool = new ForkJoinPool()
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
    pool.shutdown()
  }

  "Multi-producer sending" in {
    val n = 40000000
    val bang = new CountDownLatch(1)

    var countdown = n
    val countdownActor = actor[Tick] {
      (t: Tick) =>
        countdown -= 1
        if (countdown == 0) {
          bang.countDown()
        }
    }

    implicit val pool = new ForkJoinPool()
    timed("Multi-producer sending", n) {
      val countdown = countdownActor
      val tick = Tick()
      (1 to n).par.foreach(i => countdown ! tick)
      bang.await()
    }
    pool.shutdown()
  }

  "Ping between actors" in {
    val gameOver = new CountDownLatch(1)

    val player =
      (b: BallZ) => b match {
        case BallZ(0, _, _) => gameOver.countDown()
        case BallZ(i, p1, p2) => p1 ! BallZ(i - 1, p2, p1)
      }

    val ping = actor[BallZ](player)
    val pong = actor[BallZ](player)
    val n = 20000000
    implicit val pool = new ForkJoinPool()
    timed("Ping between actors", n) {
      ping ! BallZ(n, pong, ping)
      gameOver.await()
    }
    pool.shutdown()
  }
}