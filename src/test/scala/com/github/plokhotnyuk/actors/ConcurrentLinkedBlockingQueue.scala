package com.github.plokhotnyuk.actors

import java.util
import java.util.concurrent.{TimeUnit, Semaphore, BlockingQueue}
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import java.util.{ConcurrentModificationException, NoSuchElementException}

/**
 * A partial implementation of concurrent blocking queue which is suitable for using in
 * ThreadPoolExecutor and ExecutorCompletionService replacement of LinkedBlockingQueue that
 * decrease latency of submitting and running of tasks.
 *
 * Implementation of task queue based on structure of non-intrusive MPSC node-based queue, described by Dmitriy Vyukov:
 * http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue
 *
 * Idea of using of semaphores for control of queue access borrowed from implementation of ThreadManager of JActor2:
 * https://github.com/laforge49/JActor2/blob/master/jactor-impl/src/main/java/org/agilewiki/jactor/impl/ThreadManagerImpl.java
 */
class ConcurrentLinkedBlockingQueue[A] extends util.AbstractQueue[A] with BlockingQueue[A] {
  private val head = new AtomicReference[Node[A]](new Node())
  private val count = new ReducibleSemaphore
  private val tail = new AtomicReference[Node[A]](head.get)
  private val none: A = null.asInstanceOf[A]

  def offer(e: A): Boolean = {
    if (e == null) throw new NullPointerException
    val n = new Node(e)
    head.getAndSet(n).lazySet(n)
    count.release()
    true
  }

  def put(e: A) {
    offer(e)
  }

  def offer(e: A, timeout: Long, unit: TimeUnit): Boolean = offer(e)

  def take(): A = {
    count.acquire()
    dequeue()
  }

  def poll(): A = throw new UnsupportedOperationException("poll")

  def poll(timeout: Long, unit: TimeUnit): A = if (count.tryAcquire(timeout, unit)) dequeue() else none

  def remainingCapacity(): Int = Integer.MAX_VALUE - size()

  @deprecated("can be safely used only for not concurrent modifications")
  def drainTo(c: util.Collection[_ >: A]): Int = drainTo(c, Integer.MAX_VALUE)

  @deprecated("can be safely used only for not concurrent modifications")
  def drainTo(c: util.Collection[_ >: A], maxElements: Int): Int =
    if (c eq null) throw new NullPointerException()
    else if (c eq this) throw new IllegalArgumentException()
    else if (maxElements <= 0) c.size()
    else {
      val tn = tail.get
      val n = tn.get
      if ((n ne null) && tail.compareAndSet(tn, n)) {
        count.reducePermit()
        c.add(n.a)
        drainTo(c, maxElements - 1)
      } else drainTo(c, maxElements)
    }

  def peek(): A = {
    val n = peekNode()
    if (n ne null) n.a else none
  }

  def iterator(): util.Iterator[A] = new util.Iterator[A] {
    private var n = peekNode()

    def hasNext: Boolean = n ne null

    def next(): A = {
      if (n eq null) throw new NoSuchElementException()
      val a = n.a
      n = n.get
      a
    }

    def remove() {
      throw new UnsupportedOperationException("remove")
    }
  }

  def size(): Int = count.availablePermits()

  @tailrec
  private def peekNode(): Node[A] = {
    val tn = tail.get
    val n = tn.get
    if ((n ne null) || (tn eq head.get)) n else peekNode() // Thank you √iktor! https://github.com/akka/akka/pull/1493/files
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
}

private class Node[A](var a: A = null.asInstanceOf[A]) extends AtomicReference[Node[A]]

private class ReducibleSemaphore extends Semaphore(0) {
  def reducePermit() {
    reducePermits(1)
  }
}