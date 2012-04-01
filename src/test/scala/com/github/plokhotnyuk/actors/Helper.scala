package com.github.plokhotnyuk.actors

object Helper {

  def timed[T](name: String, n: Int)(benchmark: => T) = {
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
}
