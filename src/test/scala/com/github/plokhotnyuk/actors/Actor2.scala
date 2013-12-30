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
final case class Actor2[A](handler: A => Unit, onError: Throwable => Unit = Actor2Utils.rethrowError, batch: Int = 1024)
                          (implicit strategy: Strategy) {
  @volatile private var tail = new Node[A]
  private val head = new AtomicReference(tail)

  if (batch < 1) throw new IllegalArgumentException("batch should be greater than 0")

  def toEffect: Run[A] = Run[A](a => this ! a)

  /** Alias for `apply` */
  def !(a: A): Unit = {
    val n = new Node(a)
    head.getAndSet(n).set(n)
    val t = tail
    if ((t ne null) && Actor2Utils.resetTail(this, t)) strategy(act(t))
  }

  /** Pass the message `a` to the mailbox of this actor */
  def apply(a: A): Unit = this ! a

  def contramap[B](f: B => A): Actor2[B] = new Actor2[B](b => this ! f(b), onError, batch)(strategy)

  private def act(t: Node[A]): Unit = {
    val n = batchHandle(handler, t, batch)
    if (n ne t) {
      n.a = null.asInstanceOf[A]
      strategy(act(n))
    } else {
      tail = n
      if ((n.get ne null) && Actor2Utils.resetTail(this, n)) strategy(act(n))
    }
  }

  @annotation.tailrec
  private def batchHandle(h: A => Unit, t: Node[A], i: Int): Node[A] = {
    val n = t.get
    if ((n ne null) && i != 0) {
      try h(n.a) catch {
        case ex: Throwable => onError(ex)
      }
      batchHandle(h, n, i - 1)
    } else t
  }
}

private class Node[A](var a: A = null.asInstanceOf[A]) extends AtomicReference[Node[A]]

private object Actor2Utils {
  import scala.concurrent.util.Unsafe

  private val unsafe = Unsafe.instance
  private val tailOffset = unsafe.objectFieldOffset(classOf[Actor2[_]].getDeclaredField("tail"))

  val rethrowError: Throwable => Unit = throw _

  def resetTail[A](a: Actor2[A], t: Node[A]): Boolean = unsafe.compareAndSwapObject(a, tailOffset, t, null)
}

object Actor2 extends ActorFunctions2 with ActorInstances2

trait ActorInstances2 {
  implicit def actorContravariant: Contravariant[Actor2] = new Contravariant[Actor2] {
    def contramap[A, B](r: Actor2[A])(f: B => A): Actor2[B] = r contramap f
  }
}

trait ActorFunctions2 {
  def actor[A](handler: A => Unit, onError: Throwable => Unit = Actor2Utils.rethrowError, batch: Int = 1024)
              (implicit s: Strategy): Actor2[A] = new Actor2[A](handler, onError, batch)(s)

  implicit def ToFunctionFromActor[A](a: Actor2[A]): A => Unit = a ! _
}
