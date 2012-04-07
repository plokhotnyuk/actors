package com.github.plokhotnyuk.actors

import java.util.concurrent.ConcurrentLinkedQueue
import annotation.tailrec

class MPMCQueue[T] extends ConcurrentLinkedQueue[T] {
  def enqueue(data: T) {
    offer(data)
  }

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