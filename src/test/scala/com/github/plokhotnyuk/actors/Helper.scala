package com.github.plokhotnyuk.actors

object Helper {
  val CPUs = Runtime.getRuntime.availableProcessors()
  val halfOfCPUs = CPUs / 2

  def timed(title: String, n: Int)(benchmark: => Unit) {
    printf("\n%s:\n", title)
    val t = System.nanoTime
    benchmark
    val d = System.nanoTime - t
    printf("%,d ns\n", d)
    printf("%,d ops\n", n)
    printf("%,d ns/op\n", d / n)
    printf("%,d ops/s\n", (n * 1000000000L) / d)
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
