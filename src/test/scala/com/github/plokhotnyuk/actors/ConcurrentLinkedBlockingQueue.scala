package com.github.plokhotnyuk.actors

import java.util
import java.util.concurrent.{TimeUnit, Semaphore, BlockingQueue}
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

/**
 * A high performance implementation of concurrent blocking queue.
 *
 * Implementation of task queue based on structure of non-intrusive MPSC node-based queue, described by Dmitriy Vyukov:
 * http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue
 *
 * Idea of using of semaphores for control of queue access borrowed from implementation of ThreadManager from JActor2:
 * https://github.com/laforge49/JActor2/blob/master/jactor-impl/src/main/java/org/agilewiki/jactor/impl/ThreadManagerImpl.java
 */
class ConcurrentLinkedBlockingQueue[A] extends util.AbstractQueue[A] with BlockingQueue[A] {
  private val head = new AtomicReference[Node[A]](new Node())
  private val counter = new ReducibleSemaphore
  private val tail = new AtomicReference[Node[A]](head.get)
  private val none: A = null.asInstanceOf[A]

  def offer(e: A): Boolean = {
    val n = new Node(e)
    head.getAndSet(n).lazySet(n)
    counter.release()
    true
  }

  def put(e: A) {
    offer(e)
  }

  def offer(e: A, timeout: Long, unit: TimeUnit): Boolean = offer(e)

  def take(): A = poll()

  def poll(): A = {
    counter.acquire()
    dequeue()
  }

  @tailrec
  private def dequeue(): A = {
    val tn = tail.get
    val n = tn.get
    if ((n ne null) && tail.compareAndSet(tn, n)) {
      val a = n.a
      n.a = none
      a
    } else dequeue()
  }

  def poll(timeout: Long, unit: TimeUnit): A = {
    if (counter.tryAcquire(timeout, unit)) poll() else none
  }

  def remainingCapacity(): Int = Integer.MAX_VALUE - size()

  def drainTo(c: util.Collection[_ >: A]): Int = drainTo(c, Integer.MAX_VALUE)

  def drainTo(c: util.Collection[_ >: A], maxElements: Int): Int =
    if (maxElements <= 0) c.size()
    else {
      val tn = tail.get
      val n = tn.get
      if ((n ne null) && tail.compareAndSet(tn, n)) {
        counter.reducePermit()
        c.add(n.a)
        drainTo(c, maxElements - 1)
      } else drainTo(c, maxElements)
    }

  @tailrec
  final def peek(): A = {
    val tn = tail.get
    val n = tn.get
    if ((n ne null) || (tn eq head.get)) n.a else peek() // Thank you √iktor! https://github.com/akka/akka/pull/1493/files
  }

  def iterator(): util.Iterator[A] = new util.Iterator[A] {
    private var tn = tail.get
    private var n = tn.get

    def hasNext: Boolean = (n ne null)

    def next(): A = {
      val a = n.a
      tn = n
      n = n.get
      a
    }

    @tailrec
    final def remove() {
      if (tn.compareAndSet(n, n.get)) counter.reducePermit()
      else remove()
    }
  }

  def size(): Int = counter.availablePermits()
}

private class Node[A](var a: A = null.asInstanceOf[A]) extends AtomicReference[Node[A]]

private class ReducibleSemaphore extends Semaphore(0) {
  def reducePermit() {
    reducePermits(1)
  }
}