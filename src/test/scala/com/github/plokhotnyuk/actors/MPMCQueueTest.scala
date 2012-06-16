package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._

@RunWith(classOf[JUnitRunner])
class MPMCQueueTest extends Specification {
  val n = 100000000

  "Same producer and consumer" in {
    val q = dataQueue
    timed("Same producer and consumer", n) {
      sendReceiveData(q, n)
    }
  }


  "Single-producer sending" in {
    val q = dataQueue
    timed("Single-producer sending", n) {
      fork {
        receiveData(q, n)
      }
      sendData(q, n)
    }
  }

  "Multi-producer sending" in {
    val q = dataQueue
    timed("Multi-producer sending", n) {
      for (j <- 1 to CPUs) fork {
        receiveData(q, n / CPUs)
      }
      sendData(q, n)
    }
  }

  "Exchange between queues" in {
    val q1 = dataQueue
    val q2 = dataQueue
    timed("Exchange between queues", n) {
      fork {
        pumpData(q1, q2, n / 2)
      }
      q1.enqueue(Message())
      pumpData(q2, q1, n / 2)
    }
  }

  def dataQueue: Queue[Message] = new MPMCQueue[Message]()

  private[this] def sendReceiveData(q: Queue[Message], n: Int) {
    val m = Message()
    var i = n
    while (i > 0) {
      q.enqueue(m)
      q.dequeue()
      i -= 1
    }
  }

  private[this] def sendData(q: Queue[Message], n: Int) {
    var i = n
    while (i > 0) {
      q.dequeue()
      i -= 1
    }
  }

  private[this] def receiveData(q: Queue[Message], n: Int) {
    val m = Message()
    var i = n
    while (i > 0) {
      q.enqueue(m)
      i -= 1
    }
  }

  private[this] def pumpData(q1: Queue[Message], q2: Queue[Message], n: Int) {
    var i = n
    while (i > 0) {
      q1.enqueue(q2.dequeue())
      i -= 1
    }
  }
}