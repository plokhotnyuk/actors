package scalaz.concurrent

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.util.Unsafe.{instance => u}
import scalaz.Contravariant
import sun.misc.Unsafe

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
sealed trait Actor2[A] {
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
  private val rethrow: PartialFunction[Throwable, Unit] = {
    case _: InterruptedException => Thread.currentThread.interrupt()
    case e => throw e
  }

  private val ignore: Any => Unit = _ => ()

  /**
   * Create actor with unbounded message queue
   *
   * @param handler  The message handler
   * @param onError  Exception handler, called if the message handler throws any `Throwable`
   * @param strategy Execution strategy, for example, a strategy that is backed by an `ExecutorService`
   * @tparam A       The type of messages accepted by this actor
   * @return         An instance of actor
   */
  def unboundedActor[A](handler: A => Unit, onError: PartialFunction[Throwable, Unit] = rethrow)
                       (implicit strategy: Strategy): Actor2[A] =
    new UnboundedActor[A](strategy, onError, handler)

  /**
   * Create actor with bounded message queue
   *
   * @param bound      An allowed maximum number of messages in queue
   * @param handler    The message handler
   * @param onError    Exception handler, called if the message handler throws any `Throwable`
   * @param onOverflow Overflow handler, called if the queue of non-handled incoming messages is full
   * @param strategy   Execution strategy, for example, a strategy that is backed by an `ExecutorService`
   * @tparam A         The type of messages accepted by this actor
   * @return           An instance of actor
   */
  def boundedActor[A](bound: Int, handler: A => Unit, onError: PartialFunction[Throwable, Unit] = rethrow, onOverflow: A => Unit = ignore)
                     (implicit strategy: Strategy): Actor2[A] = {
    require(bound > 0, "Bound should be greater than 0")
    new BoundedActor[A](bound, strategy, onError, onOverflow, handler)
  }

  implicit def ToFunctionFromActor[A](a: Actor2[A]): A => Unit = a ! _
}

private case class UnboundedActor[A](strategy: Strategy, onError: PartialFunction[Throwable, Unit],
                                     handler: A => Unit) extends AtomicReference[Node[A]] with Actor2[A] {
  def !(a: A): Unit = {
    val n = new Node(a)
    val h = getAndSet(n)
    if (h ne null) h.lazySet(n)
    else schedule(n)
  }

  def contramap[B](f: B => A): Actor2[B] = new UnboundedActor[B](strategy, onError, b => this ! f(b))

  private def schedule(n: Node[A]): Unit = strategy(act(n))

  @annotation.tailrec
  private def act(n: Node[A], i: Int = 1024, f: A => Unit = handler, e: PartialFunction[Throwable, Unit] = onError): Unit = {
    try f(n.a) catch e
    val n2 = n.get
    if (n2 eq null) scheduleLastTry(n)
    else if (i == 0) schedule(n2)
    else act(n2, i - 1, f, e)
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

private case class BoundedActor[A](bound: Int, strategy: Strategy, onError: PartialFunction[Throwable, Unit], onOverflow: A => Unit,
                                   handler: A => Unit) extends AtomicReference[NodeWithCount[A]] with Actor2[A] {
  @volatile private var count: Int = _

  def !(a: A): Unit = checkAndAdd(new NodeWithCount(a))

  def contramap[B](f: B => A): Actor2[B] = new BoundedActor[B](bound, strategy, onError, b => onOverflow(f(b)), b => this ! f(b))

  @annotation.tailrec
  private def checkAndAdd(n: NodeWithCount[A]): Unit = {
    val tc = count
    val h = get
    if (h eq null) {
      n.count = tc + 1
      if (compareAndSet(h, n)) schedule(n)
      else checkAndAdd(n)
    } else {
      val hc = h.count
      if (hc - tc < bound) {
        n.count = hc + 1
        if (compareAndSet(h, n)) h.lazySet(n)
        else checkAndAdd(n)
      } else onOverflow(n.a)
    }
  }

  private def schedule(n: NodeWithCount[A]): Unit = strategy(act(n))

  @annotation.tailrec
  private def act(n: NodeWithCount[A], i: Int = 1024, f: A => Unit = handler, e: PartialFunction[Throwable, Unit] = onError,
                  u: Unsafe = u, o: Long = BoundedActor.countOffset): Unit = {
    u.putOrderedInt(this, o, n.count)
    try f(n.a) catch e
    val n2 = n.get
    if (n2 eq null) scheduleLastTry(n)
    else if (i == 0) schedule(n2)
    else act(n2, i - 1, f, e, u, o)
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

private object BoundedActor {
  private val countOffset = u.objectFieldOffset(classOf[BoundedActor[_]].getDeclaredField("count"))
}

private class NodeWithCount[A](val a: A) extends AtomicReference[NodeWithCount[A]] {
  var count: Int = _
}
