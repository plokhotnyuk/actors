package com.github.plokhotnyuk.actors

import java.util.concurrent.atomic.AtomicReference
import annotation.tailrec

/**
 * Version of multi producer/single consumer lock-free unbounded queue
 * based on non-intrusive MPSC node-based queue, described by Dmitriy Vyukov:
 * http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue
 *
 * @tparam A type of data to queue/dequeue
 */
class MPSCQueue[A] extends Queue[A] {
  private[this] val tail = new AtomicReference(new Node[A]())
  private[this] val head = new AtomicReference(tail.get)

  def enqueue(a: A) {
    val n = new Node(a)
    head.getAndSet(n).lazySet(n)
  }

  /**
   * CAUTION!!!
   * Active spin loop for benchmarking on dedicated core/processor.
   * Don't use it in production code,
   * because it eagerly eats CPU cycles and can prevent execution other threads on same core/processor.
   */
  @tailrec
  final def dequeue(): A = {
    val next = tail.get.get
    if (next ne null) {
      tail.set(next)
      next.a
    } else {
      dequeue()
    }
  }
}
