package com.github.plokhotnyuk.actors

class MPSCQueueSpec extends MPMCQueueSpec {
  override def messageQueue: Queue[Message] = new MPSCQueue[Message]()
}