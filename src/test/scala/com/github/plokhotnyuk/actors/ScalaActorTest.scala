package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._
import actors.{Exit, Actor}

@RunWith(classOf[JUnitRunner])
class ScalaActorTest extends Specification {
  "Single-producer sending" in {
    val n = 1000000
    timed("Single-producer sending", n) {
      val bang = new CountDownLatch(1)
      val countdown = new Actor {
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
    timed("Multi-producer sending", n) {
      val bang = new CountDownLatch(1)
      val countdownActor = new Actor {
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
      countdownActor.start()
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
  }

  "Ping between actors" in {
    val n = 1000000
    timed("Ping between actors", n) {
      val gameOver = new CountDownLatch(1)
      val ping = new Actor {
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
      val pong = new Actor {
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
      ping.start()
      pong.start()
      ping.send(Ball(n), pong)
      gameOver.await()
    }
  }

  "Single-producer asking" in {
    val n = 1000000
    timed("Single-producer asking", n) {
      val echo = new Actor {
        def act() {
          loop {
            react {
              case _@msg => sender ! msg
            }
          }
        }
      }
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
    val n = 1000000
    timed("Multi-producer asking", n) {
      val p = availableProcessors
      val done = new CountDownLatch(p)
      val echoActor = new Actor {
        def act() {
          loop {
            react {
              case _@msg => sender ! msg
            }
          }
        }
      }
      echoActor.start()
      for (j <- 1 to p) {
        fork {
          val message = Message()
          val echo = echoActor
          var i = n / p
          while (i > 0) {
            echo !? message
            i -= 1
          }
          done.countDown()
        }
      }
      done.await()
      echoActor ! Exit(null, null)
    }
  }
}