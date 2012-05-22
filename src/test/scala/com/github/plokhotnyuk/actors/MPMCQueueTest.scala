package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._

@RunWith(classOf[JUnitRunner])
class MPMCQueueTest extends Specification {
  val n = 100000000

  "Same producer and consumer" in {
    timed("Same producer and consumer", n) {
      val q = dataQueue
      val d = Data()
      var i = n
      while (i > 0) {
        q.enqueue(d)
        q.dequeue()
        i -= 1
      }
    }
  }

  "Single-producer sending" in {
    timed("Single-producer sending", n) {
      val q = dataQueue
      fork {
        receiveData(q, n)
      }
      sendData(q, n)
    }
  }

  "Multi-producer sending" in {
    timed("Multi-producer sending", n) {
      val q = dataQueue
      for (j <- 1 to CPUs) fork {
        receiveData(q, n / CPUs)
      }
      sendData(q, n)
    }
  }

  "Exchange between queues" in {
    timed("Exchange between queues", n) {
      val q1 = dataQueue
      val q2 = dataQueue
      fork {
        pumpData(q1, q2, n / 2)
      }
      q1.enqueue(Data())
      pumpData(q2, q1, n / 2)
    }
  }

  def dataQueue: Queue[Data] = new MPMCQueue[Data]()

  private[this] def sendData(q: Queue[Data], n: Int) {
    var i = n
    while (i > 0) {
      q.dequeue()
      i -= 1
    }
  }

  private[this] def receiveData(q: Queue[Data], n: Int) {
    val d = Data()
    var i = n
    while (i > 0) {
      q.enqueue(d)
      i -= 1
    }
  }

  private[this] def pumpData(q1: Queue[Data], q2: Queue[Data], n: Int) {
    var i = n
    while (i > 0) {
      q1.enqueue(q2.dequeue())
      i -= 1
    }
  }
}