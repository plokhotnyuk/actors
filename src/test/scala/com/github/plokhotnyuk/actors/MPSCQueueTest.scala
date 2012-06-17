package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class MPSCQueueTest extends MPMCQueueTest {
  override def messageQueue: Queue[Message] = new MPSCQueue[Message]()
}