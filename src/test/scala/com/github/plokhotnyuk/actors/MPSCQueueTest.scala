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
          queue.offer(data)
          queue.poll()
      }
    }
  }

  "Single-producer sending" in {
    timed("Single-producer sending", n) {
      val queue = new MPSCQueue[Data]()
      new Thread() {
        override def run() {
          val data = Data()
          (1 to n).foreach(i => queue.offer(data))
        }
      }.start()
      (1 to n).foreach(i => queue.poll())
    }
  }

  "Multi-producer sending" in {
    timed("Multi-producer sending", n) {
      val queue = new MPSCQueue[Data]()
      new Thread() {
        override def run() {
          val data = Data()
          (1 to n).par.foreach(i => queue.offer(data))
        }
      }.start()
      (1 to n).foreach(i => queue.poll())
    }
  }
}