package com.github.plokhotnyuk.actors

import java.util.concurrent.atomic.AtomicReference
import scalaz.concurrent.{Strategy, Run}
import scalaz.Contravariant
import scala.util.control.NoStackTrace

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
   * Create actor with unbounded message queue
   *
   * @param handler  The message handler
   * @param onError  Exception handler, called if the message handler throws any `Throwable`
   * @param strategy Execution strategy, for example, a strategy that is backed by an `ExecutorService`
   * @tparam A       The type of messages accepted by this actor
   * @return         An instance of actor
   */
  def unboundedActor[A](handler: A => Unit, onError: Throwable => Unit = rethrow)
                       (implicit strategy: Strategy): Actor2[A] =
    new UnboundedActor[A](handler, onError, strategy)

  /**
   * Create actor with bounded message queue
   *
   * @param handler  The message handler
   * @param onError  Exception handler, called if the message handler throws any `Throwable`
   * @param strategy Execution strategy, for example, a strategy that is backed by an `ExecutorService`
   * @param bound    An allowed maximum number of messages in queue
   * @tparam A       The type of messages accepted by this actor
   * @return         An instance of actor
   */
  def boundedActor[A](handler: A => Unit, bound: Int, onError: Throwable => Unit = rethrow)
                     (implicit strategy: Strategy): Actor2[A] = {
    if (bound <= 0) throw new IllegalArgumentException
    new BoundedActor[A](handler, onError, strategy, bound)
  }

  implicit def ToFunctionFromActor[A](a: Actor2[A]): A => Unit = a ! _
}

class OutOfMessageQueueBoundsException extends RuntimeException with NoStackTrace

private class UnboundedActor[A](handler: A => Unit, onError: Throwable => Unit,
                                strategy: Strategy) extends AtomicReference[Node[A]] with Actor2[A] {

  def !(a: A): Unit = {
    val n = new Node(a)
    val h = getAndSet(n)
    if (h ne null) h.lazySet(n)
    else schedule(n)
  }

  def contramap[B](f: B => A): Actor2[B] = new UnboundedActor[B](b => this ! f(b), onError, strategy)

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

private class BoundedActor[A](handler: A => Unit, onError: Throwable => Unit, strategy: Strategy,
                              bound: Int) extends AtomicReference[NodeWithCount[A]] with Actor2[A] {
  private var count: Int = _

  def !(a: A): Unit = {
    val n = new NodeWithCount(a)
    val h = getAndSetHead(n)
    if (h ne null) h.lazySet(n)
    else schedule(n)
  }

  def contramap[B](f: B => A): Actor2[B] = new BoundedActor[B](b => this ! f(b), onError, strategy, bound)

  @annotation.tailrec
  private def getAndSetHead(n: NodeWithCount[A]): NodeWithCount[A] = {
    val h = get
    if (h eq null) n.count = count + 1
    else if (h.count - count < bound) n.count = h.count + 1
    else throw new OutOfMessageQueueBoundsException
    if (compareAndSet(h, n)) h
    else getAndSetHead(n)
  }

  private def schedule(n: NodeWithCount[A]): Unit = strategy(act(n))

  @annotation.tailrec
  private def act(n: NodeWithCount[A], i: Int = 1024, f: A => Unit = handler): Unit = {
    count = n.count
    try f(n.a) catch {
      case ex: Throwable => onError(ex)
    }
    val n2 = n.get
    if (n2 eq null) scheduleLastTry(n)
    else if (i == 0) schedule(n2)
    else act(n2, i - 1, f)
  }

  private def scheduleLastTry(n: NodeWithCount[A]): Unit = strategy(lastTry(n))

  private def lastTry(n: NodeWithCount[A]): Unit = if (!compareAndSet(n, null)) act(next(n))

  @annotation.tailrec
  private def next(n: NodeWithCount[A]): NodeWithCount[A] = {
    val n2 = n.get
    if (n2 ne null) n2
    else next(n)
  }
}

private class NodeWithCount[A](val a: A) extends AtomicReference[NodeWithCount[A]] {
  var count: Int = _
}
