package com.github.plokhotnyuk.actors

import java.util
import java.util.concurrent.{TimeUnit, Semaphore, BlockingQueue}
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

class ConcurrentLinkedBlockingQueue[A] extends util.AbstractQueue[A] with BlockingQueue[A] {
  private val head = new AtomicReference[Node[A]](new Node())
  private val requests = new Semaphore(0)
  private val tail = new AtomicReference[Node[A]](head.get)
  private val none: A = null.asInstanceOf[A]

  def offer(e: A): Boolean = {
    val n = new Node(e)
    head.getAndSet(n).lazySet(n)
    requests.release()
    true
  }

  def put(e: A) {
    offer(e)
  }

  def offer(e: A, timeout: Long, unit: TimeUnit): Boolean = offer(e)

  def take(): A = poll()

  def poll(): A = {
    requests.acquire()
    dequeue()
  }

  @tailrec
  private def dequeue(): A = {
    val tn = tail.get
    val n = tn.get
    if ((n eq null) || !tail.compareAndSet(tn, n)) dequeue()
    else {
      val a = n.a
      n.a = none
      a
    }
  }

  def poll(timeout: Long, unit: TimeUnit): A = {
    if (requests.tryAcquire(timeout, unit)) poll() else none
  }

  def remainingCapacity(): Int = size()

  def drainTo(c: util.Collection[_ >: A]): Int = drainTo(c, Integer.MAX_VALUE)

  def drainTo(c: util.Collection[_ >: A], maxElements: Int): Int = {
    val tn = tail.get
    val n = tn.get
    if (maxElements <= 0 || (n eq null) || !tail.compareAndSet(tn, n)) c.size()
    else {
      c.add(n.a)
      drainTo(c, maxElements - 1)
    }
  }

  def peek(): A = {
    val tn = tail.get
    val n = tn.get
    if (n ne null) n.a
    else none
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
      if (!tn.compareAndSet(n, n.get)) remove()
    }
  }

  def size(): Int = size(0)

  @tailrec
  private def size(acc: Int): Int = {
    val tn = tail.get
    val n = tn.get
    if (n eq null) acc
    else size(acc + 1)
  }
}

private class Node[A](var a: A = null.asInstanceOf[A]) extends AtomicReference[Node[A]]
