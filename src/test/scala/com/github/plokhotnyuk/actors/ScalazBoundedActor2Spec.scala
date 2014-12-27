package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch
import com.github.plokhotnyuk.actors.BenchmarkSpec._
import scalaz.concurrent.Actor2
import scalaz.concurrent.Actor2._

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

  override def actor[A](f: A => Unit): Actor2[A] = boundedActor(40000000, f)
}
