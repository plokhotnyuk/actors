package com.github.plokhotnyuk.actors

import scala.concurrent.util.Unsafe
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
                          (implicit strategy: Strategy) extends Tail[A] {
  private val head = new AtomicReference(tail)

  if (batch < 1) throw new IllegalArgumentException("batch should be greater than 0")

  def toEffect: Run[A] = Run[A](a => this ! a)

  /** Alias for `apply` */
  def !(a: A): Unit = {
    val n = new Node(a)
    head.getAndSet(n).set(n)
    val t = tail
    if ((t ne null) && Actor2.resetTail(this, t)) strategy(act(t))
  }

  /** Pass the message `a` to the mailbox of this actor */
  def apply(a: A): Unit = this ! a

  def contramap[B](f: B => A): Actor2[B] = new Actor2[B](b => this ! f(b), onError, batch)(strategy)

  private def act(t: Node[A]): Unit = {
    val n = batchHandle(t, batch)
    if (n ne t) {
      n.a = null.asInstanceOf[A] // to avoid possible memory leak when queue is empty or actor waiting for execution
      strategy(act(n))
    } else {
      tail = n
      if ((n.get ne null) && Actor2.resetTail(this, n)) strategy(act(n))
    }
  }

  @annotation.tailrec
  private def batchHandle(t: Node[A], i: Int): Node[A] = {
    val n = t.get
    if (n ne null) {
      try handler(n.a) catch {
        case ex: Throwable => onError(ex)
      }
      if (i != 0) batchHandle(n, i - 1)
      else n
    } else t
  }
}

private[actors] class Tail[A] {
  @volatile protected var tail: Node[A] = new Node[A]
}

private class Node[A](var a: A = null.asInstanceOf[A]) extends AtomicReference[Node[A]]

object Actor2 extends ActorFunctions2 with ActorInstances2 {
  private val unsafe = Unsafe.instance
  private val tailOffset = unsafe.objectFieldOffset(classOf[Tail[_]].getDeclaredField("tail"))

  def resetTail[A](a: Actor2[A], t: Node[A]): Boolean = unsafe.compareAndSwapObject(a, tailOffset, t, null)
}

trait ActorInstances2 {
  implicit def actorContravariant: Contravariant[Actor2] = new Contravariant[Actor2] {
    def contramap[A, B](r: Actor2[A])(f: B => A): Actor2[B] = r contramap f
  }
}

trait ActorFunctions2 {
  def actor[A](handler: A => Unit, onError: Throwable => Unit = throw _, batch: Int = 1024)
              (implicit s: Strategy): Actor2[A] = new Actor2[A](handler, onError, batch)(s)

  implicit def ToFunctionFromActor[A](a: Actor2[A]): A => Unit = a ! _
}
