package com.github.plokhotnyuk.actors

import scalaz.Contravariant
import scalaz.concurrent.{Run, Strategy}
import annotation.tailrec
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

/**
 * Processes messages of type `A` sequentially. Messages are submitted to
 * the actor with the method `!`. Processing is typically performed asynchronously,
 * this is controlled by the provided `strategy`.
 *
 * Based on non-intrusive MPSC node-based queue, described by Dmitriy Vyukov:
 * http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue
 *
 * @see scalaz.concurrent.Promise
 *
 * @param handler  The message handler
 * @param onError  Exception handler, called if the message handler throws any `Throwable`.
 * @param strategy Execution strategy, for example, a strategy that is backed by an `ExecutorService`
 * @tparam A       The type of messages accepted by this actor.
 */
final case class Actor2[A](handler: A => Unit, onError: Throwable => Unit = throw(_), val batchSize: Int = 1024)
                         (implicit val strategy: Strategy) {
  self =>

  private val tail = new AtomicReference(new Node[A]())
  private val head = new AtomicReference(tail.get)
  private val suspended = new AtomicInteger(1)

  val toEffect: Run[A] = Run[A]((a) => this ! a)

  /** Alias for `apply` */
  def !(a: A) {
    val n = new Node(a)
    head.getAndSet(n).lazySet(n)
    trySchedule()
  }

  /** Pass the message `a` to the mailbox of this actor */
  def apply(a: A) {
    this ! a
  }

  def contramap[B](f: B => A): Actor2[B] =
    Actor2[B]((b: B) => (this ! f(b)), onError)(strategy)

  private def trySchedule() {
    if (suspended.compareAndSet(1, 0)) schedule()
  }

  private def schedule() {
    try {
      strategy(act())
    } catch {
      case ex: Throwable =>
        suspended.set(1)
        throw new RuntimeException("Cannot schedule actor for execution", ex)
    }
  }

  private def act() {
    val t = tail.get
    val n = batchHandle(t, batchSize)
    if (n ne t) {
      tail.set(n)
      schedule()
    } else {
      suspended.set(1)
      if (n.get ne null) trySchedule()
    }
  }

  @tailrec
  private def batchHandle(t: Node[A], i: Int): Node[A] = {
    val n = t.get
    if (n ne null) {
      try {
        handler(n.a)
      } catch {
        case ex: Throwable => onError(ex)
      }
      if (i > 0) batchHandle(n, i - 1) else n
    } else t
  }
}

private class Node[A](val a: A = null.asInstanceOf[A]) extends AtomicReference[Node[A]]

object Actor2 extends ActorFunctions with ActorInstances

trait ActorInstances {
  implicit def actorContravariant: Contravariant[Actor2] = new Contravariant[Actor2] {
    def contramap[A, B](r: Actor2[A])(f: (B) => A): Actor2[B] = r contramap f
  }
}

trait ActorFunctions {
  def actor[A](e: A => Unit, err: Throwable => Unit = throw (_), batchSize: Int = 1024)(implicit s: Strategy): Actor2[A] =
    Actor2[A](e, err, batchSize)

  implicit def ToFunctionFromActor[A](a: Actor2[A]): A => Unit = a ! _
}
