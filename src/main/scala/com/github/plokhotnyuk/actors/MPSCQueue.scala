package com.github.plokhotnyuk.actors

import java.util.concurrent.atomic.AtomicReference
import annotation.tailrec

/**
 * Non-intrusive MPSC node-based queue, described by Dmitriy Vyukov:
 * http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue
 *
 * @tparam T type of data to queue/dequeue
 */
class MPSCQueue[T] {
  private[this] var tail = new Node[T]()
  private[this] val head = new AtomicReference[Node[T]](tail)

  def enqueue(data: T) {
    val node = new Node[T]()
    node.data = data
    head.getAndSet(node).next = node
  }

  @tailrec
  final def dequeue(): T = {
    val next = tail.next
    if (next ne null) {
      tail = next
      next.data
    } else {
      dequeue()
    }
  }
}

private[actors] class Node[T]() {
  var data: T = _
  @volatile var next: Node[T] = _
}