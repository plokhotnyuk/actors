package com.github.plokhotnyuk.actors

import com.typesafe.config.ConfigFactory._
import com.typesafe.config.Config

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
            mailbox-type = "akka.actor.NonBlockingBoundedMailbox"
            mailbox-bound = 10000000
          }
        }
      }
    """))
}
