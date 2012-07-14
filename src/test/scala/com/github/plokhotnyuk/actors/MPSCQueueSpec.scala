package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class MPSCQueueSpec extends MPMCQueueSpec {
  override def messageQueue: Queue[Message] = new MPSCQueue[Message]()
}