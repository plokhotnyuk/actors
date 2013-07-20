package com.github.plokhotnyuk.actors

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scalaz.concurrent.Strategy
import scalaz.concurrent.Run
import scalaz.Contravariant

/**
 * Processes messages of type `A` sequentially. Messages are submitted to
 * the actor with the method `!`. Processing is typically performed asynchronously,
 * this is controlled by the provided `strategy`.
 *
 * Implementation based on non-intrusive MPSC node-based queue, described by Dmitriy Vyukov:
 * http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue
 *
 * @see scalaz.concurrent.Promise
 *
 * @param handler  The message handler
 * @param onError  Exception handler, called if the message handler throws any `Throwable`.
 * @param strategy Execution strategy, for example, a strategy that is backed by an `ExecutorService`
 * @tparam A       The type of messages accepted by this actor.
 */
final case class Actor2[A](handler: A => Unit, onError: Throwable => Unit = throw _)
                         (implicit val strategy: Strategy) {
  self =>

  @volatile private var tail = new Node[A]()
  private val state = new AtomicInteger() // actor state: 0 - suspended, 1 - running
  private val head = new AtomicReference(tail)

  def toEffect: Run[A] = Run[A](a => this ! a)

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

  def contramap[B](f: B => A): Actor2[B] = new Actor2[B]((b: B) => this ! f(b), onError)(strategy)

  private def trySchedule() {
    if (state.compareAndSet(0, 1)) schedule()
  }

  private def schedule() {
    strategy(act())
  }

  private def act() {
    val n = batchHandle(tail, 1024)
    if (n ne tail) {
      setTail(n)
      schedule()
    } else {
      state.set(0)
      if (n.get ne null) trySchedule()
    }
  }

  @annotation.tailrec
  private def batchHandle(t: Node[A], i: Int): Node[A] = {
    val n = t.get
    if (n ne null) {
      try {
        handler(n.a)
      } catch {
        case ex: Throwable =>
          setTail(n)
          onError(ex)
      }
      if (i > 0) batchHandle(n, i - 1) else n
    } else t
  }

  private def setTail(n: Node[A]) {
    tail = n
    n.a = null.asInstanceOf[A]
  }
}

private class Node[A](var a: A = null.asInstanceOf[A]) extends AtomicReference[Node[A]]

object Actor2 extends ActorFunctions2 with ActorInstances2

trait ActorInstances2 {
  implicit def actorContravariant: Contravariant[Actor2] = new Contravariant[Actor2] {
    def contramap[A, B](r: Actor2[A])(f: B => A): Actor2[B] = r contramap f
  }
}

trait ActorFunctions2 {
  def actor[A](e: A => Unit, err: Throwable => Unit = throw _)(implicit s: Strategy): Actor2[A] =
    new Actor2[A](e, err)

  implicit def ToFunctionFromActor[A](a: Actor2[A]): A => Unit = a ! _
}
