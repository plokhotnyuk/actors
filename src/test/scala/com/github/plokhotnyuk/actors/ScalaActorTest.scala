package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import actors.Actor
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._

@RunWith(classOf[JUnitRunner])
class ScalaActorTest extends Specification with AvailableProcessorsParallelism {
  "Single-producer sending" in {
    case class Tick()

    val n = 1000000
    val bang = new CountDownLatch(1)

    class Countdown extends Actor {
      private[this] var countdown = n

      def act() {
        loop {
          react {
            case Tick() =>
              countdown -= 1
              if (countdown == 0) {
                bang.countDown()
                exit()
              }
          }
        }
      }
    }

    val countdown = new Countdown
    countdown.start()
    timed("Single-producer sending", n) {
      (1 to n).foreach(i => countdown ! Tick())
      bang.await()
    }
  }

  "Multi-producer sending" in {
    case class Tick()

    val n = 1000000
    val bang = new CountDownLatch(1)

    class Countdown extends Actor {
      private[this] var countdown = n

      def act() {
        loop {
          react {
            case Tick() =>
              countdown -= 1
              if (countdown == 0) {
                bang.countDown()
                exit()
              }
          }
        }
      }
    }

    val countdown = new Countdown
    countdown.start()
    timed("Multi-producer sending", n) {
      (1 to n).par.foreach(i => countdown ! Tick())
      bang.await()
    }
  }

  "Ping between actors" in {
    case class Ball(hitCountdown: Int)

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

    val ping = new Player
    val pong = new Player
    ping.start()
    pong.start()
    val n = 1000000
    timed("Ping between actors", n) {
      ping.send(Ball(n), pong)
      gameOver.await()
    }
  }

  "Single-producer asking" in {
    case class Message(content: Any)

    case class PoisonPill()

    class Echo extends Actor {
      def act() {
        loop {
          react {
            case Message(c) => sender ! Message(c)
            case PoisonPill() => exit()
          }
        }
      }
    }

    val echo = new Echo
    echo.start()
    val n = 1000000
    timed("Single-producer asking", n) {
      (1 to n).foreach(i => echo !? Message(i))
    }
    echo ! PoisonPill()
  }

  "Multi-producer asking" in {
    case class Message(content: Any)

    case class PoisonPill()

    class Echo extends Actor {
      def act() {
        loop {
          react {
            case Message(c) => sender ! Message(c)
            case PoisonPill() => exit()
          }
        }
      }
    }

    val echo = new Echo
    echo.start()
    val n = 1000000
    timed("Multi-producer asking", n) {
      (1 to n).par.foreach(i => echo !? Message(i))
    }
    echo ! PoisonPill()
  }
}

