package com.github.plokhotnyuk.actors

object Helper {
  val availableProcessors = Runtime.getRuntime.availableProcessors()

  def timed[T](name: String, n: Int)(benchmark: => T): T = {
    printf("\n%s:\n", name)
    val start = System.nanoTime
    val result = benchmark
    val duration = System.nanoTime - start
    printf("%,d ns\n", duration)
    printf("%,d ops\n", n)
    printf("%,d ns/op\n", duration / n)
    printf("%,d ops/s\n", (n * 1000000000L) / duration)
    result
  }

  def fork[T](code: => Unit): Thread = {
    val thread = new Thread {
      override def run() {
        code
      }
    }
    thread.start()
    thread
  }
}
