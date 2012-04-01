package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._
import java.lang.Long

@RunWith(classOf[JUnitRunner])
class EventBasedActorTest extends Specification {
  "Single-producer sending" in {
    case class Tick()

    val n = 20000000
    val bang = new CountDownLatch(1)

    class Countdown extends EventBasedActor {
      private[this] var countdown = n

      def receive = {
        case Tick() => {
          countdown -= 1
          if (countdown == 0) {
            bang.countDown()
          }
        }
      }
    }

    EventProcessor.initPool(2)
    val countdown = new Countdown
    timed("Single-producer sending", n) {
      (1 to n).foreach(i => countdown ! Tick())
      bang.await()
    }
    EventProcessor.shutdownPool()
  }

  "Multi-producer sending" in {
    case class Tick()

    val n = 20000000
    val bang = new CountDownLatch(1)

    class Countdown extends EventBasedActor {
      private[this] var countdown = n

      def receive = {
        case Tick() => {
          countdown -= 1
          if (countdown == 0) {
            bang.countDown()
          }
        }
      }
    }

    EventProcessor.initPool(1)
    val countdown = new Countdown
    timed("Multi-producer sending", n) {
      (1 to n).par.foreach(i => countdown ! Tick())
      bang.await()
    }
    EventProcessor.shutdownPool()
  }

  "Ping between actors" in {
    case class Ball(hitCountdown: Int)

    val gameOver = new CountDownLatch(1)

    class Player extends EventBasedActor {
      def receive = {
        case Ball(0) => gameOver.countDown()
        case Ball(i) => reply(Ball(i - 1))
      }
    }

    EventProcessor.initPool(2)
    val ping = new Player
    val pong = new Player
    val n = 20000000
    timed("Ping between actors", n) {
      ping.send(Ball(n), pong)
      gameOver.await()
    }
    EventProcessor.shutdownPool()
  }

  "Single-producer asking" in {
    case class Message(content: Any)

    class Echo extends EventBasedActor {
      def receive = {
        case Message(c) => reply(Message(c))
      }
    }

    EventProcessor.initPool(2)
    val echo = new Echo
    val n = 10000000
    timed("Single-producer asking", n) {
      (1 to n).foreach(i => echo ? Message(i))
    }
    EventProcessor.shutdownPool()
  }

  "Multi-producer asking" in {
    case class Message(content: Any)

    class Echo extends EventBasedActor {
      def receive = {
        case Message(c) => reply(Message(c))
      }
    }

    EventProcessor.initPool(1)
    val echo = new Echo
    val n = 10000000
    timed("Multi-producer asking", n) {
      (1 to n).par.foreach(i => echo ? Message(i))
    }
    EventProcessor.shutdownPool()
  }

  "Actor creation" in {
    EventProcessor.initPool(2)

    case class Sum(start: Long, end: Long)

    case class Result(sum: Long)

    class Recursion extends EventBasedActor {
      def receive = {
        case Sum(start, end) =>
          val middle = (start + end) / 2
          val left = if (start == middle) start else Recursion.sum(start, middle)
          val right = if (middle + 1 == end) end else Recursion.sum(middle + 1, end)
          val sum = left + right
          reply(Result(sum))
      }
    }

    object Recursion {
      def sum(start: Long, end: Long): Long = {
        (new Recursion ? Sum(start, end)) match {
          case Result(sum) => sum
        }
      }
    }

    val n = 10000000
    timed("Actor creation", n) {
      assert(Recursion.sum(0, n) == ((1L + n) * n) / 2)
    }
    EventProcessor.shutdownPool()
  }
}