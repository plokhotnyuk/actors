package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._

@RunWith(classOf[JUnitRunner])
class ThreadBasedActorTest extends Specification with AvailableProcessorsParallelism {
  "Single-producer sending" in {
    val n = 100000000
    timed("Single-producer sending", n) {
      val bang = new CountDownLatch(1)
      val countdown = new ThreadBasedActor {
        private[this] var countdown = n

        def receive = {
          case _ =>
            countdown -= 1
            if (countdown == 0) {
              bang.countDown()
              exit()
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
    val n = 100000000
    timed("Multi-producer sending", n) {
      val bang = new CountDownLatch(1)
      val countdown = new ThreadBasedActor {
        private[this] var countdown = n

        def receive = {
          case _ =>
            countdown -= 1
            if (countdown == 0) {
              bang.countDown()
              exit()
            }
        }
      }
      val tick = Tick()
      (1 to n).par.foreach(i => countdown ! tick)
      bang.await()
    }
  }

  "Ping between actors" in {
    val n = 100000000
    timed("Ping between actors", n) {
      val gameOver = new CountDownLatch(1)
      val ping = new ThreadBasedActor {
        def receive = {
          case Ball(0) => gameOver.countDown(); exit()
          case Ball(1) => reply(Ball(0)); exit()
          case Ball(i) => reply(Ball(i - 1))
        }
      }
      val pong = new ThreadBasedActor {
        def receive = {
          case Ball(0) => gameOver.countDown(); exit()
          case Ball(1) => reply(Ball(0)); exit()
          case Ball(i) => reply(Ball(i - 1))
        }
      }
      ping.send(Ball(n), pong)
      gameOver.await()
    }
  }

  "Single-producer asking" in {
    val n = 20000000
    timed("Single-producer asking", n) {
      val echo = new ThreadBasedActor {
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
      echo.exit()
    }
  }

  "Multi-producer asking" in {
    val n = 20000000
    timed("Multi-producer asking", n) {
      val echo = new ThreadBasedActor {
        def receive = {
          case _@msg => reply(msg)
        }
      }
      val message = Message()
      (1 to n).par.foreach(i => echo ? message)
      echo.exit()
    }
  }

  "Max throughput" in {
    val n = 100000000
    timed("Max throughput", n) {
      val p = availableProcessors / 2
      val bang = new CountDownLatch(p)
      for (j <- 1 to p) {
        fork {
          val countdown = new ThreadBasedActor {
            private[this] var countdown = n / p

            def receive = {
              case _ =>
                countdown -= 1
                if (countdown == 0) {
                  bang.countDown()
                  exit()
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
  }
}