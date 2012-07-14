package com.github.plokhotnyuk.actors

import com.typesafe.config.Config

class AkkaActor2Spec extends AkkaActorSpec {
  override def config: Config = createConfig("com.github.plokhotnyuk.actors.UnboundedMailbox2")
}