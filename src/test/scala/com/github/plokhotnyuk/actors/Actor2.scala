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
final case class Actor2[A](handler: A => Unit, onError: Throwable => Unit = throw _, batch: Int = 1024)
                          (implicit strategy: Strategy) {
  if (batch < 1) throw new IllegalArgumentException("batch should be greater than 0")
  private val tail = new AtomicReference(new Node[A])
  private val head = new AtomicReference(tail.get)

  def toEffect: Run[A] = Run[A](a => this ! a)

  /** Alias for `apply` */
  def !(a: A): Unit = {
    val n = new Node(a)
    head.getAndSet(n).lazySet(n)
    val t = tail.getAndSet(null)
    if (t ne null) schedule(t)
  }

  /** Pass the message `a` to the mailbox of this actor */
  def apply(a: A): Unit = this ! a

  def contramap[B](f: B => A): Actor2[B] = new Actor2[B](b => this ! f(b), onError)(strategy)

  private def schedule(t: Node[A]): Unit = strategy(act(t, batch))

  @annotation.tailrec
  private def act(t: Node[A], i: Int): Unit = {
    val n = t.get
    if ((n ne null) && i != 0) {
      try handler(n.a) catch {
        case ex: Throwable => onError(ex)
      }
      act(n, i - 1)
    } else {
      t.a = null.asInstanceOf[A]
      if (t.get ne null) schedule(t)
      else {
        tail.set(t)
        if ((t.get ne null) && tail.compareAndSet(t, null)) schedule(t)
      }
    }
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
  def actor[A](handler: A => Unit, onError: Throwable => Unit = throw _, batch: Int = 1024)
              (implicit s: Strategy): Actor2[A] = new Actor2[A](handler, onError)(s)

  implicit def ToFunctionFromActor[A](a: Actor2[A]): A => Unit = a ! _
}
