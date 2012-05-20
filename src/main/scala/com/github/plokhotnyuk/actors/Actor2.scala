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
final case class Actor2[A](e: A => Unit, onError: Throwable => Unit = throw (_), batchSize: Int = 1024)(implicit val strategy: Strategy) {
  private[this] var anyMsg: A = _  // Don't know how to simplify this
  @volatile private[this] var tail = new Node2[A](anyMsg)
  private[this] val head = new AtomicReference[Node2[A]](tail)
  private[this] val working = new AtomicInteger()

  val toEffect: Effect[A] = effect[A](a => this ! a)

  def apply(a: A) {
    this ! a
  }

  def !(a: A) {
    val node = new Node2[A](a)
    head.getAndSet(node).lazySet(node)
    trySchedule()
  }

  private[this] def trySchedule(): Any = if (working.compareAndSet(0, 1))
    try { act(()) } catch {
      case ex =>
        working.set(0)
        throw new RuntimeException(ex)
    }

  private[this] val act: Effect[Unit] = effect {
    (u: Unit) =>
      tail = batchWork(tail, batchSize)
      working.set(0)
      if (tail.get ne null) trySchedule()
  }

  @tailrec
  private[this] def batchWork(current: Node2[A], i: Int): Node2[A] = {
    val next = current.get
    if (next ne null) {
      handleMessage(next.a)
      if (i > 0) batchWork(next, i - 1) else next
    } else current
  }

  private[this] def handleMessage(a: A) {
    try {
      e(a)
    } catch { case ex => onError(ex) }
  }
}

private[actors] class Node2[A](val a: A) extends AtomicReference[Node2[A]]

trait Actors2 {
  def actor2[A](e: A => Unit, err: Throwable => Unit = throw (_), batchSize: Int = 1024)(implicit s: Strategy): Actor2[A] = Actor2[A](e, err, batchSize)

  implicit def ActorFrom2[A](a: Actor2[A]): A => Unit = a ! _
}

object Scalaz2 extends Actors2
