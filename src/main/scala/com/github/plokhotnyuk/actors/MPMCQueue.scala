package com.github.plokhotnyuk.actors

import java.util.concurrent.ConcurrentLinkedQueue
import annotation.tailrec

class MPMCQueue[T] extends ConcurrentLinkedQueue[T] {
  def enqueue(data: T) {
    offer(data)
  }

  /**
   * CAUTION!!!
   * Active spin loop for benchmarking on dedicated core/processor.
   * Don't use it in production code,
   * because it eagerly eats CPU cycles and can prevent execution other threads on same core/processor.
   */
  @tailrec
  final def dequeue(): T = {
    val data = poll()
    if (data != null) {
      data
    } else {
      dequeue()
    }
  }
}