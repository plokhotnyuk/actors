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
    val l = new CountDownLatch(1)
    val a = tickActor(l, n)
    timed("Single-producer sending", n) {
      sendTicks(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = 40000000
    val l = new CountDownLatch(1)
    val a = tickActor(l, n)
    timed("Multi-producer sending", n) {
      for (j <- 1 to CPUs) fork {
        sendTicks(a, n / CPUs)
      }
      l.await()
    }
  }

  "Ping between actors" in {
    val n = 20000000
    val l = new CountDownLatch(2)
    val p1 = playerActor(l, n / 2)
    val p2 = playerActor(l, n / 2)
    timed("Ping between actors", n) {
      p1.tell(Message(), p2)
      l.await()
    }
  }

  "Single-producer asking" in {
    val n = 1000000
    val a = echoActor
    timed("Single-producer asking", n) {
      requestEchos(a, n)
    }
  }

  "Multi-producer asking" in {
    val n = 1000000
    val l = new CountDownLatch(CPUs)
    val a = echoActor
    timed("Multi-producer asking", n) {
      for (j <- 1 to CPUs) fork {
        requestEchos(a, n / CPUs)
        l.countDown()
      }
      l.await()
    }
  }

  "Max throughput" in {
    val n = 40000000
    val l = new CountDownLatch(halfOfCPUs)
    timed("Max throughput", n) {
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
    val m = Message()
    var i = n
    while (i > 0) {
      a ! m
      i -= 1
    }
  }

  private[this] def playerActor(l: CountDownLatch, n: Int): ActorRef =
    actorSystem.actorOf(Props(new Actor {
      private[this] var i = n

      def receive = {
        case m =>
          sender ! m
          i -= 1
          if (i == 0) {
            l.countDown()
            context.stop(self)
          }
      }
    }))

  private[this] def echoActor: ActorRef =
    actorSystem.actorOf(Props(new Actor {
      def receive = {
        case m => sender ! m
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