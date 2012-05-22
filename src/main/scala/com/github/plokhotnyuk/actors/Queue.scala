package com.github.plokhotnyuk.actors

trait Queue[T] {
  def enqueue(data: T)

  /**
   * CAUTION!!!
   * Active spin loop for benchmarking on dedicated core/processor.
   * Don't use it in production code,
   * because it eagerly eats CPU cycles and can prevent execution other threads on same core/processor.
   */
  def dequeue(): T
}
