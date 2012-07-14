package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import com.typesafe.config.Config

@RunWith(classOf[JUnitRunner])
class AkkaActor2Spec extends AkkaActorSpec {
  override def config: Config = createConfig("com.github.plokhotnyuk.actors.UnboundedMailbox2")
}