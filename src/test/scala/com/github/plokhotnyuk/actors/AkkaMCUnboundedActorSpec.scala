package com.github.plokhotnyuk.actors

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory._

class AkkaMCUnboundedActorSpec extends AkkaUnboundedActorSpec {
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
            mailbox-type = "akka.dispatch.MultiConsumerUnboundedMailbox"
          }
        }
      }
    """))
}
