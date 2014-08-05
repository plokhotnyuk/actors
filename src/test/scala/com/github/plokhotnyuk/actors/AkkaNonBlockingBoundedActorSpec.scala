package com.github.plokhotnyuk.actors

import com.typesafe.config.ConfigFactory._
import com.typesafe.config.Config

class AkkaNonBlockingBoundedActorSpec extends AkkaBoundedActorSpec {
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
            mailbox-type = "akka.dispatch.NonBlockingBoundedMailbox"
            mailbox-capacity = 10000000
          }
          benchmark-dispatcher-2 {
            executor = "com.github.plokhotnyuk.actors.CustomExecutorServiceConfigurator"
            throughput = 1024
            mailbox-type = "akka.dispatch.NonBlockingBoundedMailbox"
            mailbox-capacity = 1
          }
        }
      }
    """))
}
