package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._

@RunWith(classOf[JUnitRunner])
class MPMCQueueTest extends Specification {
  val n = 100000000

  "Same producer and consumer" in {
    val q = messageQueue
    timed("Same producer and consumer", n) {
      sendReceiveMessages(q, n)
    }
  }

  "Single-producer sending" in {
    val q = messageQueue
    timed("Single-producer sending", n) {
      fork {
        receiveMessages(q, n)
      }
      sendMessages(q, n)
    }
  }

  "Multi-producer sending" in {
    val q = messageQueue
    timed("Multi-producer sending", n) {
      for (j <- 1 to CPUs) fork {
        receiveMessages(q, n / CPUs)
      }
      sendMessages(q, n)
    }
  }

  "Exchange between queues" in {
    val q1 = messageQueue
    val q2 = messageQueue
    timed("Exchange between queues", n) {
      fork {
        pumpMessages(q1, q2, n / 2)
      }
      q1.enqueue(Message())
      pumpMessages(q2, q1, n / 2)
    }
  }

  def messageQueue: Queue[Message] = new MPMCQueue[Message]()

  private[this] def sendReceiveMessages(q: Queue[Message], n: Int) {
    val m = Message()
    var i = n
    while (i > 0) {
      q.enqueue(m)
      q.dequeue()
      i -= 1
    }
  }

  private[this] def sendMessages(q: Queue[Message], n: Int) {
    var i = n
    while (i > 0) {
      q.dequeue()
      i -= 1
    }
  }

  private[this] def receiveMessages(q: Queue[Message], n: Int) {
    val m = Message()
    var i = n
    while (i > 0) {
      q.enqueue(m)
      i -= 1
    }
  }

  private[this] def pumpMessages(q1: Queue[Message], q2: Queue[Message], n: Int) {
    var i = n
    while (i > 0) {
      q1.enqueue(q2.dequeue())
      i -= 1
    }
  }
}