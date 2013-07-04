package com.github.plokhotnyuk.actors

import java.util
import java.util.concurrent.{TimeUnit, BlockingQueue}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import java.util.concurrent.locks.{LockSupport, ReentrantLock}

class ConcurrentLinkedBlockingQueue[A](totalSpins: Int,
                                       slowdownSpins: Int) extends util.AbstractQueue[A] with BlockingQueue[A] {
  private val head = new AtomicReference[Node[A]](new Node())
  private val count = new AtomicInteger()
  private val notEmptyLock = new ReentrantLock()
  private val notEmptyCondition = notEmptyLock.newCondition()
  private val tail = new AtomicReference[Node[A]](head.get)

  def size(): Int = count.get

  def put(a: A) {
    if (a == null) throw new NullPointerException()
    val n = new Node(a)
    head.getAndSet(n).lazySet(n)
    if (count.getAndIncrement() == 0) signalNotEmpty()
  }

  def take(): A = take(totalSpins)

  def drainTo(c: util.Collection[_ >: A]): Int = drainTo(c, Integer.MAX_VALUE)

  @annotation.tailrec
  final def drainTo(c: util.Collection[_ >: A], maxElements: Int): Int =
    if (maxElements <= 0) c.size()
    else {
      val tn = tail.get
      val n = tn.get
      if (n eq null) c.size()
      else if (tail.compareAndSet(tn, n)) {
        count.getAndDecrement()
        c.add(n.a)
        n.a = null.asInstanceOf[A]
        drainTo(c, maxElements - 1)
      } else drainTo(c, maxElements)
    }

  @annotation.tailrec
  final def poll(): A = {
    val tn = tail.get
    val n = tn.get
    if (n eq null) null.asInstanceOf[A]
    else if (tail.compareAndSet(tn, n)) {
      count.getAndDecrement()
      val a = n.a
      n.a = null.asInstanceOf[A]
      a
    } else poll()
  }

  def peek(): A = {
    val n = tail.get.get
    if (n eq null) null.asInstanceOf[A]
    else n.a
  }

  def offer(e: A): Boolean = {
    put(e)
    true
  }

  def offer(e: A, timeout: Long, unit: TimeUnit): Boolean = offer(e)

  def poll(timeout: Long, unit: TimeUnit): A = poll(totalSpins, unit.toNanos(timeout))

  def remainingCapacity(): Int = Integer.MAX_VALUE - count.get

  def iterator(): util.Iterator[A] = new util.Iterator[A] {
    private var n = tail.get.get

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

  @annotation.tailrec
  private def take(i: Int): A = {
    val tn = tail.get
    val n = tn.get
    if ((n ne null) && tail.compareAndSet(tn, n)) {
      count.getAndDecrement()
      val a = n.a
      n.a = null.asInstanceOf[A]
      a
    } else take(if (backOff(i)) i - 1 else totalSpins)
  }

  @annotation.tailrec
  private def poll(i: Int, ns: Long): A = {
    val tn = tail.get
    val n = tn.get
    if ((n ne null) && tail.compareAndSet(tn, n)) {
      count.getAndDecrement()
      val a = n.a
      n.a = null.asInstanceOf[A]
      a
    } else {
      val lastTimeout = backOff(i, ns)
      if (lastTimeout > 0) poll(i - 1, lastTimeout)
      else null.asInstanceOf[A]
    }
  }

  private def backOff(i: Int): Boolean = {
    if (i > slowdownSpins) true
    else if (i == slowdownSpins) {
      Thread.`yield`()
      true
    } else if (i > 0) {
      LockSupport.parkNanos(1)
      true
    } else {
      waitUntilEmpty()
      false
    }
  }

  private def backOff(i: Int, ns: Long): Long = {
    if (i > slowdownSpins) ns
    else if (i == slowdownSpins) {
      val now = System.nanoTime()
      Thread.`yield`()
      ns - (System.nanoTime() - now)
    } else if (i > 0) {
      val now = System.nanoTime()
      LockSupport.parkNanos(1)
      ns - (System.nanoTime() - now)
    } else {
      waitUntilEmpty(ns)
      0
    }
  }

  private def waitUntilEmpty() {
    notEmptyLock.lockInterruptibly()
    try {
      await()
    } finally {
      notEmptyLock.unlock()
    }
  }

  private def waitUntilEmpty(ns: Long) {
    notEmptyLock.lockInterruptibly()
    try {
      awaitNanos(ns)
    } finally {
      notEmptyLock.unlock()
    }
  }

  @annotation.tailrec
  private def await() {
    if (count.get == 0) {
      notEmptyCondition.await()
      await()
    }
  }

  @annotation.tailrec
  private def awaitNanos(ns: Long) {
    if (ns > 0 && count.get == 0) {
      awaitNanos(notEmptyCondition.awaitNanos(ns))
    }
  }

  private def signalNotEmpty() {
    notEmptyLock.lock()
    try {
      notEmptyCondition.signal()
    } finally {
      notEmptyLock.unlock()
    }
  }
}

private class Node[A](var a: A = null.asInstanceOf[A]) extends AtomicReference[Node[A]]
