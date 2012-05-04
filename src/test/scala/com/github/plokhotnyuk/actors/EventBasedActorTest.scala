package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._

@RunWith(classOf[JUnitRunner])
class EventBasedActorTest extends Specification {
  "Single-producer sending" in {
    val n = 100000000
    EventProcessor.initPool(1)
    timed("Single-producer sending", n) {
      val bang = new CountDownLatch(1)
      val countdown = new EventBasedActor {
        private[this] var countdown = n

        def receive = {
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
    EventProcessor.shutdownPool()
  }

  "Multi-producer sending" in {
    val n = 100000000
    EventProcessor.initPool(1)
    timed("Multi-producer sending", n) {
      val bang = new CountDownLatch(1)
      val countdownActor = new EventBasedActor {
        private[this] var countdown = n

        def receive = {
          case _ =>
            countdown -= 1
            if (countdown == 0) {
              bang.countDown()
            }
        }
      }
      val p = availableProcessors
      for (j <- 1 to p) {
        fork {
          val tick = Tick()
          val countdown = countdownActor
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

  "Ping between actors" in {
    val n = 100000000
    EventProcessor.initPool(2)
    timed("Ping between actors", n) {
      val gameOver = new CountDownLatch(1)
      val ping = new EventBasedActor {
        def receive = {
          case Ball(0) => gameOver.countDown()
          case Ball(i) => reply(Ball(i - 1))
        }
      }
      val pong = new EventBasedActor {
        def receive = {
          case Ball(0) => gameOver.countDown()
          case Ball(i) => reply(Ball(i - 1))
        }
      }
      ping.send(Ball(n), pong)
      gameOver.await()
    }
    EventProcessor.shutdownPool()
  }

  "Single-producer asking" in {
    val n = 20000000
    EventProcessor.initPool(1)
    timed("Single-producer asking", n) {
      val echo = new EventBasedActor {
        def receive = {
          case _@msg => reply(msg)
        }
      }
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
    val n = 20000000
    EventProcessor.initPool(1)
    timed("Multi-producer asking", n) {
      val p = availableProcessors
      val done = new CountDownLatch(p)
      val echoActor = new EventBasedActor {
        def receive = {
          case _@msg => reply(msg)
        }
      }
      for (j <- 1 to p) {
        fork {
          val message = Message()
          val echo = echoActor
          var i = n / p
          while (i > 0) {
            echo ? message
            i -= 1
          }
          done.countDown()
        }
      }
      done.await()
    }
    EventProcessor.shutdownPool()
  }

  "Max throughput" in {
    val n = 100000000
    val p = availableProcessors / 2
    EventProcessor.initPool(p)
    timed("Max throughput", n) {
      val bang = new CountDownLatch(p)
      for (j <- 1 to p) {
        fork {
          val countdown = new EventBasedActor() {
            private[this] var countdown = n / p

            def receive = {
              case _ =>
                countdown -= 1
                if (countdown == 0) {
                  bang.countDown()
                }
            }
          }
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