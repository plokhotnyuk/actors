package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import net.liftweb.actor.LiftActor
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._

@RunWith(classOf[JUnitRunner])
class LiftActorTest extends Specification with AvailableProcessorsParallelism {
  "Single-producer sending" in {
    val n = 20000000
    timed("Single-producer sending", n) {
      val bang = new CountDownLatch(1)
      val countdown = new LiftActor {
        private[this] var countdown = n

        def messageHandler = {
          case _ =>
            countdown -= 1
            if (countdown == 0) {
              bang.countDown()
            }
        }
      }
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
    val n = 20000000
    timed("Multi-producer sending", n) {
      val bang = new CountDownLatch(1)
      val countdown = new LiftActor {
        private[this] var countdown = n

        def messageHandler = {
          case _ =>
            countdown -= 1
            if (countdown == 0) {
              bang.countDown()
            }
        }
      }
      val tick = Tick()
      (1 to n).par.foreach(i => countdown ! tick)
      bang.await()
    }
  }

  "Ping between actors" in {
    val n = 2000000
    timed("Ping between actors", n) {
      val gameOver = new CountDownLatch(1)
      var pong: LiftActor = null
      val ping = new LiftActor {
        def messageHandler = {
          case Ball(0) => gameOver.countDown()
          case Ball(i) => pong.send(Ball(i - 1))
        }
      }
      pong = new LiftActor {
        def messageHandler = {
          case Ball(0) => gameOver.countDown()
          case Ball(i) => ping.send(Ball(i - 1))
        }
      }
      ping.send(Ball(n))
      gameOver.await()
    }
  }

  "Single-producer asking" in {
    val n = 1000000
    timed("Single-producer asking", n) {
      val echo = new LiftActor {
        def messageHandler = {
          case _@msg => reply(msg)
        }
      }
      val message = Message()
      var i = n
      while (i > 0) {
        echo !? message
        i -= 1
      }
    }
  }

  "Multi-producer asking" in {
    val n = 2000000
    timed("Multi-producer asking", n) {
      val echo = new LiftActor {
        def messageHandler = {
          case _@msg => reply(msg)
        }
      }
      val message = Message()
      (1 to n).par.foreach(i => echo !? message)
    }
  }
}