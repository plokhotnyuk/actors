package com.github.plokhotnyuk.actors

import com.github.plokhotnyuk.actors.Actor2._
import com.github.plokhotnyuk.actors.BenchmarkSpec._
import java.util.concurrent.CountDownLatch

class ScalazBoundedActor2Spec extends ScalazUnboundedActor2Spec {
  "Overflow throughput" in {
    val n = 100000000
    val l = new CountDownLatch(1)
    timed(n) {
      val a = boundedActor(1, (_: Message) => l.await())
      val t = Message()
      var i = n
      while (i > 0) {
        a ! t
        i -= 1
      }
    }
    l.countDown()
  }

  override def actor[A](handler: A => Unit): Actor2[A] = boundedActor(40000000, handler)
}
