package com.github.plokhotnyuk.actors

import java.util.concurrent.atomic.AtomicReference
import scalaz.concurrent.{Strategy, Run}
import scalaz.Contravariant

/**
 * Processes messages of type `A`, one at a time. Messages are submitted to
 * the actor with the method `!`. Processing is typically performed asynchronously,
 * this is controlled by the provided `strategy`.
 *
 * Memory consistency guarantee: when each message is processed by the `handler`, any memory that it
 * mutates is guaranteed to be visible by the `handler` when it processes the next message, even if
 * the `strategy` runs the invocations of `handler` on separate threads. This is achieved because
 * the `Actor` reads a volatile memory location before entering its event loop, and writes to the same
 * location before suspending.
 *
 * Implementation based on non-intrusive MPSC node-based queue, described by Dmitriy Vyukov:
 * [[http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue]]
 *
 * @see scalaz.concurrent.Promise for a use case.
 */
trait Actor2[A] {
  /** Pass the message `a` to the mailbox of this actor */
  def !(a: A): Unit

  def contramap[B](f: B => A): Actor2[B]

  def apply(a: A): Unit = this ! a

  def toEffect: Run[A] = Run[A](a => this ! a)
}

object Actor2 extends ActorInstances2 with ActorFunctions2

sealed abstract class ActorInstances2 {
  implicit val actorContravariant: Contravariant[Actor2] = new Contravariant[Actor2] {
    def contramap[A, B](r: Actor2[A])(f: B => A): Actor2[B] = r contramap f
  }
}

trait ActorFunctions2 {
  private val rethrow: Throwable => Unit = throw _

  /**
   * Create actor
   *
   * @param handler  The message handler
   * @param onError  Exception handler, called if the message handler throws any `Throwable`.
   * @param strategy Execution strategy, for example, a strategy that is backed by an `ExecutorService`
   * @tparam A       The type of messages accepted by this actor.
   * @return         An instance of actor
   */
  def actor[A](handler: A => Unit, onError: Throwable => Unit = rethrow)
              (implicit strategy: Strategy): Actor2[A] = new DefaultActor2[A](handler, onError)(strategy)

  implicit def ToFunctionFromActor[A](a: Actor2[A]): A => Unit = a ! _
}

private class DefaultActor2[A](handler: A => Unit, onError: Throwable => Unit)
                              (implicit val strategy: Strategy) extends AtomicReference[Node[A]] with Actor2[A] {
  def !(a: A): Unit = {
    val n = new Node(a)
    val h = getAndSet(n)
    if (h ne null) h.lazySet(n)
    else schedule(n)
  }

  def contramap[B](f: B => A): Actor2[B] = new DefaultActor2[B](b => this ! f(b), onError)(strategy)

  private def schedule(n: Node[A]): Unit = strategy(act(n))

  @annotation.tailrec
  private def act(n: Node[A], i: Int = 1024, f: A => Unit = handler): Unit = {
    try f(n.a) catch {
      case ex: Throwable => onError(ex)
    }
    val n2 = n.get
    if (n2 eq null) scheduleLastTry(n)
    else if (i == 0) schedule(n2)
    else act(n2, i - 1, f)
  }

  private def scheduleLastTry(n: Node[A]): Unit = strategy(lastTry(n))

  private def lastTry(n: Node[A]): Unit = if (!compareAndSet(n, null)) act(next(n))

  @annotation.tailrec
  private def next(n: Node[A]): Node[A] = {
    val n2 = n.get
    if (n2 ne null) n2
    else next(n)
  }
}

private class Node[A](val a: A) extends AtomicReference[Node[A]]
