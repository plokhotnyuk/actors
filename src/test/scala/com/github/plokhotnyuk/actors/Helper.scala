package com.github.plokhotnyuk.actors

import org.specs2.execute.{Success, Result}

object Helper {
  val CPUs = Runtime.getRuntime.availableProcessors()
  val halfOfCPUs = CPUs / 2

  def timed(title: String, n: Int)(benchmark: => Unit): Result = {
    printf("\n%s:\n", title)
    val t = System.nanoTime
    benchmark
    val d = System.nanoTime - t
    printf("%,d ns\n", d)
    printf("%,d ops\n", n)
    printf("%,d ns/op\n", d / n)
    printf("%,d ops/s\n", (n * 1000000000L) / d)
    Success("success")
  }

  def fork(code: => Unit): Thread = {
    val t = new Thread {
      override def run() {
        code
      }
    }
    t.start()
    t
  }
}
