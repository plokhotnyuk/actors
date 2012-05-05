package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._
import com.typesafe.config.ConfigFactory._
import com.typesafe.config.Config

@RunWith(classOf[JUnitRunner])
class AkkaActor2Test extends AkkaActorTest {
  override def config: Config = load(parseString( """
  akka {
    daemonic = on
    actor.default-dispatcher {
      mailbox-type = com.github.plokhotnyuk.actors.UnboundedMailbox2
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
}