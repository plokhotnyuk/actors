package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._

@RunWith(classOf[JUnitRunner])
class EventBasedActorTest extends Specification with AvailableProcessorsParallelism {
  "Single-producer sending" in {
    val n = 100000000
    val bang = new CountDownLatch(1)

    class Countdown extends EventBasedActor {
      private[this] var countdown = n

      def receive = {
        case _ =>
          countdown -= 1
          if (countdown == 0) {
            bang.countDown()
          }
      }
    }

    EventProcessor.initPool(1)
    timed("Single-producer sending", n) {
      val countdown = new Countdown()
      val tick = Tick()
      var i = n
      while (i > 0) {
        countdown ! tick
        i -= 1
      }
      bang.await()
    }
    EventProcessor.shutdownPool()
  }

  "Multi-producer sending" in {
    val n = 100000000
    val bang = new CountDownLatch(1)

    class Countdown extends EventBasedActor {
      private[this] var countdown = n

      def receive = {
        case _ =>
          countdown -= 1
          if (countdown == 0) {
            bang.countDown()
          }
      }
    }

    EventProcessor.initPool(1)
    timed("Multi-producer sending", n) {
      val countdown = new Countdown()
      val tick = Tick()
      (1 to n).par.foreach(i => countdown ! tick)
      bang.await()
    }
    EventProcessor.shutdownPool()
  }

  "Ping between actors" in {
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
    val n = 100000000
    timed("Ping between actors", n) {
      ping.send(Ball(n), pong)
      gameOver.await()
    }
    EventProcessor.shutdownPool()
  }

  "Single-producer asking" in {
    class Echo extends EventBasedActor {
      def receive = {
        case _@message => reply(message)
      }
    }

    EventProcessor.initPool(1)
    val n = 20000000
    timed("Single-producer asking", n) {
      val echo = new Echo()
      val message = Message()
      var i = n
      while (i > 0) {
        echo ? message
        i -= 1
      }
    }
    EventProcessor.shutdownPool()
  }

  "Multi-producer asking" in {
    class Echo extends EventBasedActor {
      def receive = {
        case _@message => reply(message)
      }
    }

    EventProcessor.initPool(1)
    val n = 20000000
    timed("Multi-producer asking", n) {
      val echo = new Echo
      val message = Message()
      (1 to n).par.foreach(i => echo ? message)
    }
    EventProcessor.shutdownPool()
  }

  "Max throughput" in {
    val n = 100000000
    val p = availableProcessors / 2
    val bang = new CountDownLatch(p)

    class Countdown extends EventBasedActor {
      private[this] var countdown = n / p

      def receive = {
        case _ =>
          countdown -= 1
          if (countdown == 0) {
            bang.countDown()
          }
      }
    }

    EventProcessor.initPool(p)
    timed("Max throughput", n) {
      for (j <- 1 to p) {
        fork {
          val countdown = new Countdown()
          val tick = Tick()
          var i = n / p
          while (i > 0) {
            countdown ! tick
            i -= 1
          }
        }
      }
      bang.await()
    }
    EventProcessor.shutdownPool()
  }
}