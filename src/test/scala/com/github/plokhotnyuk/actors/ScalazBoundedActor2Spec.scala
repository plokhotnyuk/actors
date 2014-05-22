package com.github.plokhotnyuk.actors

import com.github.plokhotnyuk.actors.Actor2._
import com.github.plokhotnyuk.actors.BenchmarkSpec._

class ScalazBoundedActor2Spec extends ScalazUnboundedActor2Spec {
  "Overflow throughput" in {
    val n = 16000000
    timed(n) {
      val a = boundedActor(1, (_: Message) => ())
      val t = Message()
      var i = n
      while (i > 0) {
        try a ! t catch {
          case ex: Throwable => // ignore
        }
        i -= 1
      }
    }
  }

  override def actor[A](handler: A => Unit): Actor2[A] = boundedActor(40000000, handler)
}
