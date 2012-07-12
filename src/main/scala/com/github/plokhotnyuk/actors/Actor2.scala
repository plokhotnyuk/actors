package com.github.plokhotnyuk.actors

import scalaz.Scalaz
import Scalaz._
import scalaz.concurrent.{Strategy, Effect}
import annotation.tailrec
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

/**
 * Based on non-intrusive MPSC node-based queue, described by Dmitriy Vyukov:
 * http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue
 */
final case class Actor2[A](e: A => Unit, err: Throwable => Unit = throw (_))(implicit s: Strategy) {
  private[this] var anyA: A = _ // Don't know how to simplify this
  @volatile private[this] var tail = new Node(anyA)
  private[this] val head = new AtomicReference[Node[A]](tail)
  private[this] val suspended = new AtomicInteger(1)

  val toEffect: Effect[A] = effect[A](a => this ! a)

  def apply(a: A) {
    this ! a
  }

  def !(a: A) {
    val n = new Node(a)
    head.getAndSet(n).lazySet(n)
    trySchedule()
  }

  private[this] def trySchedule() {
    if (suspended.compareAndSet(1, 0)) schedule()
  }

  private[this] def schedule() {
    try {
      s(act())
    } catch {
      case ex =>
        suspended.set(1)
        throw new RuntimeException(ex)
    }
  }

  private[this] def act() {
    val n = batchHandle(tail, 1024)
    if (n ne tail) {
      tail = n
      schedule()
    } else {
      suspended.set(1)
      if (n.get ne null) trySchedule()
    }
  }

  @tailrec
  private[this] def batchHandle(n: Node[A], i: Int): Node[A] = {
    val next = n.get
    if (next ne null) {
      try {
        e(next.a)
      } catch {
        case ex => err(ex)
      }
      if (i > 0) batchHandle(next, i - 1) else next
    } else n
  }
}

trait Actors2 {
  def actor2[A](e: A => Unit, err: Throwable => Unit = throw (_))(implicit s: Strategy): Actor2[A] =
    Actor2[A](e, err)

  implicit def ActorFrom2[A](a: Actor2[A]): A => Unit = a ! _
}

object Scalaz2 extends Actors2
