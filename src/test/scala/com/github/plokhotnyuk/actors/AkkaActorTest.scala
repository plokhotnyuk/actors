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
  implicit val timeout = Timeout(Duration(1, TimeUnit.SECONDS))
  val config = load(parseString("""
  akka.actor.default-dispatcher {
    throughput = 1024
  }
  """))

  "Single-producer sending" in {
    val n = 40000000
    val bang = new CountDownLatch(1)

    class Countdown extends Actor {
      private[this] var countdown = n

      def receive = {
        case _ =>
          countdown -= 1
          if (countdown == 0) {
            bang.countDown()
            context.stop(self)
          }
      }
    }

    val actorSystem = ActorSystem("system", config)
    timed("Single-producer sending", n) {
      val countdown = actorSystem.actorOf(Props(new Countdown), "countdown")
      val tick = Tick()
      var i = n
      while (i > 0) {
        countdown ! tick
        i -= 1
      }
      bang.await()
    }
    actorSystem.shutdown()
  }

  "Multi-producer sending" in {
    val n = 40000000
    val bang = new CountDownLatch(1)

    class Countdown extends Actor {
      private[this] var countdown = n

      def receive = {
        case _ =>
          countdown -= 1
          if (countdown == 0) {
            bang.countDown()
            context.stop(self)
          }
      }
    }

    val actorSystem = ActorSystem("system", config)
    timed("Multi-producer sending", n) {
      val countdown = actorSystem.actorOf(Props(new Countdown), "countdown")
      val tick = Tick()
      (1 to n).par.foreach(i => countdown ! tick)
      bang.await()
    }
    actorSystem.shutdown()
  }

  "Ping between actors" in {
    val gameOver = new CountDownLatch(1)

    class Player extends Actor {
      def receive = {
        case Ball(0) => gameOver.countDown(); context.stop(self)
        case Ball(1) => sender ! Ball(0); context.stop(self)
        case Ball(i) => sender ! Ball(i - 1)
      }
    }

    val actorSystem = ActorSystem("system", config)
    val ping = actorSystem.actorOf(Props(new Player), "ping")
    val pong = actorSystem.actorOf(Props(new Player), "pong")
    val n = 20000000
    timed("Ping between actors", n) {
      ping.tell(Ball(n), pong)
      gameOver.await()
    }
    actorSystem.shutdown()
  }

  "Single-producer asking" in {
    class Echo extends Actor {
      def receive = {
        case _@message => sender ! message
      }
    }

    val actorSystem = ActorSystem("system", config)
    val n = 1000000
    timed("Single-producer asking", n) {
      val echo = actorSystem.actorOf(Props(new Echo), "echo")
      val message = Message()
      val oneSec = Duration(1, TimeUnit.SECONDS)
      var i = n
      while (i > 0) {
        Await.result(echo ? message, oneSec)
        i -= 1
      }
    }
    actorSystem.shutdown()
  }

  "Multi-producer asking" in {
    class Echo extends Actor {
      def receive = {
        case _@message => sender ! message
      }
    }

    val actorSystem = ActorSystem("system", config)
    val n = 1000000
    timed("Multi-producer asking", n) {
      val echo = actorSystem.actorOf(Props(new Echo), "echo")
      val message = Message()
      val oneSec = Duration(1, TimeUnit.SECONDS)
      (1 to n).par.foreach(i => Await.result(echo ? message, oneSec))
    }
    actorSystem.shutdown()
  }
}