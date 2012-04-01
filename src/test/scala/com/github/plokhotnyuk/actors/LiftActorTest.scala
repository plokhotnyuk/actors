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
    case class Tick()

    val n = 10000000
    val bang = new CountDownLatch(1)

    class Countdown extends LiftActor {
      private[this] var countdown = n

      def messageHandler = {
        case Tick() => {
          countdown -= 1
          if (countdown == 0) {
            bang.countDown()
          }
        }
      }
    }
    val countdown = new Countdown
    timed("Single-producer sending", n) {
      (1 to n).foreach(i => countdown ! Tick())
      bang.await()
    }
  }

  "Multi-producer sending" in {
    case class Tick()

    val n = 10000000
    val bang = new CountDownLatch(1)

    class Countdown extends LiftActor {
      private[this] var countdown = n

      def messageHandler = {
        case Tick() => {
          countdown -= 1
          if (countdown == 0) {
            bang.countDown()
          }
        }
      }
    }
    val countdown = new Countdown
    timed("Multi-producer sending", n) {
      (1 to n).par.foreach(i => countdown ! Tick())
      bang.await()
    }
  }

  "Ping between actors" in {
    case class Ball(hitCountdown: Int)

    val gameOver = new CountDownLatch(1)

    class Player extends LiftActor {
      var competitor: Player = _

      def messageHandler = {
        case Ball(0) => gameOver.countDown()
        case Ball(i) => competitor.send(Ball(i - 1))
      }
    }

    val ping = new Player
    val pong = new Player
    ping.competitor = pong
    pong.competitor = ping
    val n = 10000000
    timed("Ping between actors", n) {
      ping.send(Ball(n))
      gameOver.await()
    }
  }

  "Single-producer asking" in {
    case class Message(content: Any)

    class Echo extends LiftActor {
      def messageHandler = {
        case Message(c) => reply(Message(c))
      }
    }

    val echo = new Echo
    val n = 1000000
    timed("Single-producer asking", n) {
      (1 to n).foreach(i => echo !? Message(i))
    }
  }

  "Multi-producer asking" in {
    case class Message(content: Any)

    class Echo extends LiftActor {
      def messageHandler = {
        case Message(c) => reply(Message(c))
      }
    }

    val echo = new Echo
    val n = 1000000
    timed("Multi-producer asking", n) {
      (1 to n).par.foreach(i => echo !? Message(i))
    }
  }
}