package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._
import scalaz._
import concurrent.{Actor, Strategy}
import Scalaz._
import java.util.concurrent.{Executors, CountDownLatch}

@RunWith(classOf[JUnitRunner])
class ScalazActorTest extends Specification {

  "Single-producer sending" in {
    case class Tick()

    val n = 20000000
    val bang = new CountDownLatch(1)

    var countdown = n
    val countdownActor = actor[Tick] {
      (t : Tick) => t match {
        case Tick() => {
          countdown -= 1
          if (countdown == 0) {
            bang.countDown()
          }
        }
      }
    }

    implicit val pool = Executors.newFixedThreadPool(2)
    implicit val strategy = Strategy.Executor
    timed("Single-producer sending", n) {
      (1 to n).foreach(i => countdownActor ! Tick())
      bang.await()
    }
    pool.shutdown()
  }

  "Multi-producer sending" in {
    case class Tick()

    val n = 20000000
    val bang = new CountDownLatch(1)

    var countdown = n
    val countdownActor = actor[Tick] {
      (t : Tick) => t match {
        case Tick() => {
          countdown -= 1
          if (countdown == 0) {
            bang.countDown()
          }
        }
      }
    }

    implicit val pool = Executors.newFixedThreadPool(1)
    implicit val strategy = Strategy.Executor
    timed("Multi-producer sending", n) {
      (1 to n).par.foreach(i => countdownActor ! Tick())
      bang.await()
    }
    pool.shutdown()
  }

  "Ping between actors" in {
    case class Ball(hitCountdown: Int, player1: Actor[Ball], player2: Actor[Ball])

    val gameOver = new CountDownLatch(1)

    val player =
      (b : Ball) => b match {
        case Ball(0, _, _) => gameOver.countDown()
        case Ball(i, p1, p2) => p1 ! Ball(i - 1, p2, p1)
      }

    val ping = actor[Ball](player)
    val pong = actor[Ball](player)
    val n = 20000000
    implicit val pool = Executors.newFixedThreadPool(2)
    implicit val strategy = Strategy.Executor
    timed("Ping between actors", n) {
      ping ! Ball(n, pong, ping)
      gameOver.await()
    }
    pool.shutdown()
  }
}