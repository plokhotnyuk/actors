package com.github.plokhotnyuk.actors

import akka.actor._
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._
import java.util.concurrent.{TimeUnit, CountDownLatch}
import akka.dispatch.Await
import akka.util.{Timeout, Duration}
import akka.pattern.ask
import com.typesafe.config.ConfigFactory._

@RunWith(classOf[JUnitRunner])
class AkkaActorTest extends Specification with AvailableProcessorsParallelism {
  implicit val timeout = Timeout(Duration(10, TimeUnit.SECONDS))
  val config = load(parseString("""
  akka {
    daemonic = on
    actor.default-dispatcher {
      executor = "fork-join-executor"
      fork-join-executor {
        parallelism-min = 1
        parallelism-factor = 1.0
        parallelism-max = %d
      }
      throughput = 1024
    }
  }
  """.format(availableProcessors)))
  val actorSystem = ActorSystem("system", config)

  "Single-producer sending" in {
    val n = 50000000
    timed("Single-producer sending", n) {
      val bang = new CountDownLatch(1)
      val countdown = actorSystem.actorOf(Props(new Actor {
        private[this] var countdown = n

        def receive = {
          case _ =>
            countdown -= 1
            if (countdown == 0) {
              bang.countDown()
              context.stop(self)
            }
        }
      }), "countdown1")
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
    val n = 50000000
    timed("Multi-producer sending", n) {
      val bang = new CountDownLatch(1)
      val countdown = actorSystem.actorOf(Props(new Actor {
        private[this] var countdown = n

        def receive = {
          case _ =>
            countdown -= 1
            if (countdown == 0) {
              bang.countDown()
              context.stop(self)
            }
        }
      }), "countdown2")
      val tick = Tick()
      (1 to n).par.foreach(i => countdown ! tick)
      bang.await()
    }
  }

  "Ping between actors" in {
    val n = 25000000
    timed("Ping between actors", n) {
      val gameOver = new CountDownLatch(1)
      val ping = actorSystem.actorOf(Props(new Actor {
        def receive = {
          case Ball(0) => gameOver.countDown()
          case Ball(i) => sender ! Ball(i - 1)
        }
      }), "ping")
      val pong = actorSystem.actorOf(Props(new Actor {
        def receive = {
          case Ball(0) => gameOver.countDown()
          case Ball(i) => sender ! Ball(i - 1)
        }
      }), "pong")
      ping.tell(Ball(n), pong)
      gameOver.await()
    }
  }

  "Single-producer asking" in {
    val n = 1000000
    timed("Single-producer asking", n) {
      val echo = actorSystem.actorOf(Props(new Actor {
        def receive = {
          case _@msg => sender ! msg
        }
      }), "echo1")
      val message = Message()
      val tenSec = Duration(10, TimeUnit.SECONDS)
      var i = n
      while (i > 0) {
        Await.result(echo ? message, tenSec)
        i -= 1
      }
    }
  }

  "Multi-producer asking" in {
    val n = 1000000
    timed("Multi-producer asking", n) {
      val echo = actorSystem.actorOf(Props(new Actor {
        def receive = {
          case _@msg => sender ! msg
        }
      }), "echo2")
      val message = Message()
      val tenSec = Duration(10, TimeUnit.SECONDS)
      (1 to n).par.foreach(i => Await.result(echo ? message, tenSec))
    }
  }

  "Max throughput" in {
    val n = 50000000
    timed("Max throughput", n) {
      val p = availableProcessors / 2
      val bang = new CountDownLatch(p)
      for (j <- 1 to p) {
        fork {
          val countdown = actorSystem.actorOf(Props(new Actor {
            private[this] var countdown = n / p

            def receive = {
              case _ =>
                countdown -= 1
                if (countdown == 0) {
                  bang.countDown()
                  context.stop(self)
                }
            }
          }), "countdown" + j)
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