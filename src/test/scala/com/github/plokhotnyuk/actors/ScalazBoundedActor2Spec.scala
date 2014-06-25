package com.github.plokhotnyuk.actors

import com.github.plokhotnyuk.actors.Actor2._
import com.github.plokhotnyuk.actors.BenchmarkSpec._
import java.util.concurrent.CountDownLatch

import org.specs2.execute.Success

class ScalazBoundedActor2Spec extends ScalazUnboundedActor2Spec {
  "Overflow throughput" in {
    val n = 100000000
    val l = new CountDownLatch(1)
    val a = boundedActor(1, (_: Message) => l.await())
    timed(n) {
      sendMessages(a, n)
    }
    l.countDown()
    Success()
  }

  override def actor[A](handler: A => Unit): Actor2[A] = boundedActor(40000000, handler)
}
