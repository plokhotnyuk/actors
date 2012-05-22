package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._

@RunWith(classOf[JUnitRunner])
class MPSCQueueTest extends MPMCQueueTest {
  override def dataQueue: Queue[Data] = new MPSCQueue[Data]()
}