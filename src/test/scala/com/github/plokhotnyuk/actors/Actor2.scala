package com.github.plokhotnyuk.actors

import java.util.concurrent.atomic.AtomicReference
import scalaz.concurrent.{Strategy, Run}
import scalaz.Contravariant

/**
 * Processes messages of type `A` sequentially. Messages are submitted to the actor with the method `!`.
 * Processing is typically performed asynchronously, this is controlled by the provided `strategy`.
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
final case class Actor2[A](handler: A => Unit, onError: Throwable => Unit = Actor2.rethrowError)
                          (implicit strategy: Strategy) {
  private val head = new AtomicReference[Node[A]]

  def toEffect: Run[A] = Run[A](a => this ! a)

  /** Alias for `apply` */
  def !(a: A): Unit = {
    val n = new Node(a)
    val h = head.getAndSet(n)
    if (h ne null) h.lazySet(n)
    else schedule(n)
  }

  /** Pass the message `a` to the mailbox of this actor */
  def apply(a: A): Unit = this ! a

  def contramap[B](f: B => A): Actor2[B] = new Actor2[B](b => this ! f(b), onError)(strategy)

  private def schedule(t: Node[A]): Unit = strategy(act(t, 1024))

  @annotation.tailrec
  private def reschedule(t: Node[A], n: Node[A]): Unit =
    if (n ne null) schedule(n)
    else if (!head.compareAndSet(t, null)) reschedule(t, t.get)

  @annotation.tailrec
  private def act(t: Node[A], i: Int): Unit = {
    try handler(t.a) catch {
      case ex: Throwable => onError(ex)
    }
    val n = t.get
    if ((n ne null) & i != 0) act(n, i - 1)
    else reschedule(t, n)
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
  val rethrowError: Throwable => Unit = throw _

  def actor[A](handler: A => Unit, onError: Throwable => Unit = rethrowError)
              (implicit s: Strategy): Actor2[A] = new Actor2[A](handler, onError)(s)

  implicit def ToFunctionFromActor[A](a: Actor2[A]): A => Unit = a ! _
}
