package com.github.plokhotnyuk.actors

import annotation.tailrec
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}

/**
 * Based on non-intrusive MPSC node-based queue, described by Dmitriy Vyukov:
 * http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue
 */
final class ThreadBasedActor[A : Manifest](e: A => Unit, onError: Throwable => Unit = throw (_)) {
  private[this] val doRun = new PaddedAtomicLong(1L)
  private[this] var spin = 1024L
  private[this] var anyMsg: A = _ // Don't know how to simplify this
  private[this] var tail = new Node[A](anyMsg)
  private[this] val head = new PaddedAtomicReference[Node[A]](tail)

  start()

  def !(msg: A) {
    val mail = new Node[A](msg)
    head.getAndSet(mail).lazySet(mail)
  }

  def exit() {
    doRun.set(0L)
  }

  private[this] def start() {
    new Thread() {
      override def run() {
        handleMessages()
      }
    }.start()
  }

  private[this] def handleMessages() {
    while (doRun.get != 0L) {
      tail = batchHandle(tail, 1024)
    }
  }

  private[this] def backOff() {
    spin = if (spin > 0L) spin - 1L else {Thread.`yield`(); 1024L}
  }

  @tailrec
  private[this] def batchHandle(curr: Node[A], i: Int): Node[A] = {
    val next = curr.get
    if (next ne null) {
      handle(next.a)
      if (i > 0) {
        batchHandle(next, i - 1)
      } else next
    } else {
      backOff()
      curr
    }
  }

  private[this] def handle(a: A) {
    try {
      e(a)
    } catch {
      case ex => onError(ex)
    }
  }
}
