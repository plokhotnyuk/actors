package com.github.plokhotnyuk.actors

import java.util.concurrent.atomic.AtomicReference
import annotation.tailrec

/**
 * Version of multi producer/single consumer lock-free unbounded queue
 * based on non-intrusive MPSC node-based queue, described by Dmitriy Vyukov:
 * http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue
 *
 * @tparam T type of data to queue/dequeue
 */
class MPSCQueue[T] {
  private[this] var anyData: T = _  // Don't know how to simplify this
  private[this] var tail = new Node[T](anyData)
  private[this] val head = new AtomicReference[Node[T]](tail)

  def enqueue(data: T) {
    val node = new Node[T](data)
    head.getAndSet(node).lazySet(node)
  }

  /**
   * CAUTION!!!
   * Active spin loop for benchmarking on dedicated core/processor.
   * Don't use it in production code,
   * because it eagerly eats CPU cycles and can prevent execution other threads on same core/processor.
   */
  @tailrec
  final def dequeue(): T = {
    val next = tail.get
    if (next ne null) {
      tail = next
      next.data
    } else {
      dequeue()
    }
  }
}

private[actors] class Node[T](val data: T) extends AtomicReference[Node[T]]