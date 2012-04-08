package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._
import actors.{Exit, Actor}

@RunWith(classOf[JUnitRunner])
class ScalaActorTest extends Specification with AvailableProcessorsParallelism {
  "Single-producer sending" in {
    val n = 1000000
    val bang = new CountDownLatch(1)

    class Countdown extends Actor {
      private[this] var countdown = n

      def act() {
        loop {
          react {
            case _ =>
              countdown -= 1
              if (countdown == 0) {
                bang.countDown()
                exit()
              }
          }
        }
      }
    }

    timed("Single-producer sending", n) {
      val countdown = new Countdown()
      countdown.start()
      val tick = Tick()
      var i = n
      while (i > 0) {
        countdown ! tick
        i -= 1
      }
      bang.await()
    }
  }

  "Multi-producer sending" in {
    val n = 1000000
    val bang = new CountDownLatch(1)

    class Countdown extends Actor {
      private[this] var countdown = n

      def act() {
        loop {
          react {
            case _ =>
              countdown -= 1
              if (countdown == 0) {
                bang.countDown()
                exit()
              }
          }
        }
      }
    }

    timed("Multi-producer sending", n) {
      val countdown = new Countdown()
      countdown.start()
      val tick = Tick()
      (1 to n).par.foreach(i => countdown ! tick)
      bang.await()
    }
  }

  "Ping between actors" in {
    val gameOver = new CountDownLatch(1)

    class Player extends Actor {
      def act() {
        loop {
          react {
            case Ball(0) => gameOver.countDown(); exit()
            case Ball(1) => sender ! Ball(0); exit()
            case Ball(i) => sender ! Ball(i - 1)
          }
        }
      }
    }

    val ping = new Player()
    val pong = new Player()
    ping.start()
    pong.start()
    val n = 1000000
    timed("Ping between actors", n) {
      ping.send(Ball(n), pong)
      gameOver.await()
    }
  }

  "Single-producer asking" in {
    class Echo extends Actor {
      def act() {
        loop {
          react {
            case _@message => sender ! message
          }
        }
      }
    }

    val n = 1000000
    timed("Single-producer asking", n) {
      val echo = new Echo()
      echo.start()
      val message = Message()
      var i = n
      while (i > 0) {
        echo !? message
        i -= 1
      }
      echo ! Exit(null, null)
    }
  }

  "Multi-producer asking" in {
    class Echo extends Actor {
      def act() {
        loop {
          react {
            case _@message => sender ! message
          }
        }
      }
    }

    val n = 1000000
    timed("Multi-producer asking", n) {
      val echo = new Echo()
      echo.start()
      val message = Message()
      (1 to n).par.foreach(i => echo !? message)
      echo ! Exit(null, null)
    }
  }
}