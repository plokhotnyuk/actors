package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._

@RunWith(classOf[JUnitRunner])
class MPSCQueueTest extends Specification {

  case class Data()

  val n = 100000000

  "Same producer and consumer" in {
    timed("Same producer and consumer", n) {
      val queue = new MPSCQueue[Data]()
      val data = Data()
      (1 to n).foreach {
        i =>
          queue.enqueue(data)
          queue.dequeue()
      }
    }
  }

  "Single-producer sending" in {
    timed("Single-producer sending", n) {
      val queue = new MPSCQueue[Data]()
      new Thread() {
        override def run() {
          val data = Data()
          (1 to n).foreach(i => queue.enqueue(data))
        }
      }.start()
      (1 to n).foreach(i => queue.dequeue())
    }
  }

  "Multi-producer sending" in {
    timed("Multi-producer sending", n) {
      val queue = new MPSCQueue[Data]()
      new Thread() {
        override def run() {
          val data = Data()
          (1 to n).par.foreach(i => queue.enqueue(data))
        }
      }.start()
      (1 to n).foreach(i => queue.dequeue())
    }
  }

  "Exchange between queues" in {
    timed("Exchange between queues", n) {
      val queue1 = new MPSCQueue[Data]()
      val queue2 = new MPSCQueue[Data]()
      val thread1 = new Thread() {
        override def run() {
          (1 to n / 2).par.foreach(i => queue1.enqueue(queue2.dequeue()))
        }
      }
      val thread2 = new Thread() {
        override def run() {
          (1 to n / 2).par.foreach(i => queue2.enqueue(queue1.dequeue()))
        }
      }
      thread1.start()
      thread2.start()
      queue1.enqueue(Data())
      thread1.join()
      thread2.join()
    }
  }
}