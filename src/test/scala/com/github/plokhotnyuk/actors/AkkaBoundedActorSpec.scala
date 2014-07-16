package com.github.plokhotnyuk.actors

import com.typesafe.config.ConfigFactory._
import com.typesafe.config.Config
import com.github.plokhotnyuk.actors.BenchmarkSpec._
import akka.actor.{Actor, ActorRef, Props}
import java.util.concurrent.CountDownLatch

import org.specs2.execute.Success

class AkkaBoundedActorSpec extends AkkaActorSpec {
  override def config: Config = load(parseString(
    """
      akka {
        log-dead-letters = 0
        log-dead-letters-during-shutdown = off
        deployment {
          default {
            dispatcher = "akka.actor.default-dispatcher"
          }
        }
        actor {
          unstarted-push-timeout = 100s
          default-dispatcher {
            executor = "com.github.plokhotnyuk.actors.CustomExecutorServiceConfigurator"
            throughput = 1024
            mailbox-type = "akka.dispatch.BoundedMailbox"
            mailbox-capacity = 10000000
            mailbox-push-timeout-time = 0
          }
          default-dispatcher-2 {
            executor = "com.github.plokhotnyuk.actors.CustomExecutorServiceConfigurator"
            throughput = 1024
            mailbox-type = "akka.dispatch.BoundedMailbox"
            mailbox-capacity = 1
            mailbox-push-timeout-time = 0
          }
        }
      }
    """))

  "Overflow throughput" in {
    val n = 5000000
    val l = new CountDownLatch(1)
    val a = blockableCountActor2(l)
    timed(n) {
      sendMessages(a, n)
    }
    l.countDown()
    Success()
  }

  private def blockableCountActor2(l: CountDownLatch): ActorRef =
    actorOf(Props(classOf[BlockableCountAkkaActor2], l).withDispatcher("akka.actor.default-dispatcher-2"))
}

private class BlockableCountAkkaActor2(l: CountDownLatch) extends Actor {
  def receive = {
    case _ =>
      l.await()
      context.stop(self)
  }
}
