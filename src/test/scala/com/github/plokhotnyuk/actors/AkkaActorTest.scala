package com.github.plokhotnyuk.actors

import akka.actor._
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._
import java.util.concurrent.{TimeUnit, CountDownLatch}
import akka.util.{Timeout, Duration}
import akka.pattern.ask
import com.typesafe.config.ConfigFactory._
import com.typesafe.config.Config
import akka.dispatch.Await

@RunWith(classOf[JUnitRunner])
class AkkaActorTest extends Specification {
  implicit val timeout = Timeout(Duration(10, TimeUnit.SECONDS))

  def config: Config = createConfig("akka.dispatch.UnboundedMailbox")

  val actorSystem = ActorSystem("system", config)

  "Single-producer sending" in {
    val n = 40000000
    timed("Single-producer sending", n) {
      val l = new CountDownLatch(1)
      val a = tickActor(l, n)
      sendTicks(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = 40000000
    timed("Multi-producer sending", n) {
      val l = new CountDownLatch(1)
      val a = tickActor(l, n)
      for (j <- 1 to CPUs) fork {
        sendTicks(a, n / CPUs)
      }
      l.await()
    }
  }

  "Ping between actors" in {
    val n = 20000000
    timed("Ping between actors", n) {
      val l = new CountDownLatch(1)
      val p1 = playerActor(l)
      val p2 = playerActor(l)
      p1.tell(Ball(n), p2)
      l.await()
    }
  }

  "Single-producer asking" in {
    val n = 1000000
    timed("Single-producer asking", n) {
      val a = echoActor
      requestEchos(a, n)
    }
  }

  "Multi-producer asking" in {
    val n = 1000000
    timed("Multi-producer asking", n) {
      val l = new CountDownLatch(CPUs)
      val a = echoActor
      for (j <- 1 to CPUs) fork {
        requestEchos(a, n / CPUs)
        l.countDown()
      }
      l.await()
    }
  }

  "Max throughput" in {
    val n = 40000000
    timed("Max throughput", n) {
      val l = new CountDownLatch(halfOfCPUs)
      for (j <- 1 to halfOfCPUs) fork {
        val a = tickActor(l, n / halfOfCPUs)
        sendTicks(a, n / halfOfCPUs)
      }
      l.await()
    }
  }

  def createConfig(mailboxClassName: String): Config =
    load(parseString(
      """
      akka {
        daemonic = on
        actor.default-dispatcher {
          mailbox-type = %s
          executor = "fork-join-executor"
          fork-join-executor {
            parallelism-min = %d
            parallelism-max = %d
          }
          throughput = 1024
        }
      }
      """ format(mailboxClassName, CPUs, CPUs)))

  private[this] def tickActor(l: CountDownLatch, n: Int): ActorRef =
    actorSystem.actorOf(Props(new Actor() {
      private[this] var i = n

      def receive = {
        case _ =>
          i -= 1
          if (i == 0) {
            l.countDown()
            context.stop(self)
          }
      }
    }))

  private[this] def sendTicks(a: ActorRef, n: Int) {
    val tick = Tick()
    var i = n
    while (i > 0) {
      a ! tick
      i -= 1
    }
  }

  private[this] def playerActor(l: CountDownLatch): ActorRef =
    actorSystem.actorOf(Props(new Actor {
      def receive = {
        case Ball(0) => l.countDown()
        case Ball(i) => sender ! Ball(i - 1)
      }
    }))

  private[this] def echoActor: ActorRef =
    actorSystem.actorOf(Props(new Actor {
      def receive = {
        case _@m => sender ! m
      }
    }))

  private[this] def requestEchos(a: ActorRef, n: Int) {
    val m = Message()
    val d = Duration(10, TimeUnit.SECONDS)
    var i = n
    while (i > 0) {
      Await.result(a ? m, d)
      i -= 1
    }
  }
}