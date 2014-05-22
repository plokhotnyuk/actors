package com.github.plokhotnyuk.actors

import com.typesafe.config.ConfigFactory._
import com.typesafe.config.Config
import com.github.plokhotnyuk.actors.BenchmarkSpec._
import akka.actor.Props

class AkkaBoundedActorSpec extends AkkaActorSpec {
  override def config: Config = load(parseString(
    """
      akka {
        log-dead-letters = 0
        log-dead-letters-during-shutdown = off
        actor {
          unstarted-push-timeout = 100s
          benchmark-dispatcher {
            executor = "com.github.plokhotnyuk.actors.CustomExecutorServiceConfigurator"
            throughput = 1024
            mailbox-type = "akka.dispatch.BoundedMailbox"
            mailbox-capacity = 10000000
            mailbox-push-timeout-time = 0
          }
          benchmark-dispatcher-2 {
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
    val a = actorOf(Props(classOf[MinimalAkkaActor]).withDispatcher("akka.actor.benchmark-dispatcher-2"))
    timed(n) {
      sendMessages(a, n)
    }
  }
}
