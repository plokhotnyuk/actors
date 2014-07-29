package akka.dispatch

import akka.actor.{InternalActorRef, ActorRef, ActorSystem, DeadLetter}
import akka.util.Unsafe._
import java.util.concurrent.atomic.AtomicReference
import com.typesafe.config.Config
import sun.misc.Unsafe

class NonBlockingBoundedMailbox(capacity: Int = Int.MaxValue) extends MailboxType with ProducesMessageQueue[MessageQueue] {
  require(capacity > 0, "Mailbox capacity should be greater than 0")

  def this(settings: ActorSystem.Settings, config: Config) = this(config.getInt("mailbox-capacity"))

  override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue = new NBBQ(capacity)
}

private final class NBBQ(capacity: Int) extends AtomicReference(new NodeWithCount) with MessageQueue with MultipleConsumerSemantics {
  @volatile private var tail: NodeWithCount = get

  override def enqueue(receiver: ActorRef, handle: Envelope): Unit =
    if (!offer(new NodeWithCount(handle))) onOverflow(receiver, handle)

  override def dequeue(): Envelope = poll(instance, NBBQ.tailOffset)

  override def numberOfMessages: Int = Math.min(capacity, Math.max(0, get.count - tail.count))

  override def hasMessages: Boolean = tail ne get

  @annotation.tailrec
  override def cleanUp(owner: ActorRef, deadLetters: MessageQueue): Unit = {
    val e = dequeue()
    if (e ne null) {
      deadLetters.enqueue(owner, e)
      cleanUp(owner, deadLetters)
    }
  }

  @annotation.tailrec
  private def offer(n: NodeWithCount): Boolean = {
    val tc = tail.count
    val h = get
    val hc = h.count
    if (hc - tc < capacity) {
      n.count = hc + 1
      if (compareAndSet(h, n)) {
        h.lazySet(n)
        true
      } else offer(n)
    } else false
  }

  private def onOverflow(a: ActorRef, e: Envelope): Unit =
    a.asInstanceOf[InternalActorRef].provider.deadLetters.tell(DeadLetter(e.message, e.sender, a), e.sender)

  @annotation.tailrec
  private def poll(u: Unsafe, o: Long): Envelope = {
    val tn = tail
    val n = tn.get
    if (n ne null) {
      if (u.compareAndSwapObject(this, o, tn, n)) {
        val e = n.handle
        n.handle = null // to avoid possible memory leak when queue is empty
        e
      } else poll(u, o)
    } else null
  }
}

private object NBBQ {
  private val tailOffset = instance.objectFieldOffset(classOf[NBBQ].getDeclaredField("tail"))
}

private final class NodeWithCount(var handle: Envelope = null) extends AtomicReference[NodeWithCount] {
  var count: Int = _
}

class UnboundedMailbox2 extends MailboxType with ProducesMessageQueue[MessageQueue] {
  def this(settings: ActorSystem.Settings, config: Config) = this()

  override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue = new UQ
}

private final class UQ extends AtomicReference(new Node) with MessageQueue with MultipleConsumerSemantics {
  @volatile private var tail: Node = get

  override def enqueue(receiver: ActorRef, handle: Envelope): Unit = {
    val n = new Node(handle)
    getAndSet(n).lazySet(n)
  }

  override def dequeue(): Envelope = poll(instance, UQ.tailOffset)

  override def numberOfMessages: Int = count(tail, 0)

  override def hasMessages: Boolean = tail ne get

  @annotation.tailrec
  override def cleanUp(owner: ActorRef, deadLetters: MessageQueue): Unit = {
    val e = dequeue()
    if (e ne null) {
      deadLetters.enqueue(owner, e)
      cleanUp(owner, deadLetters)
    }
  }

  @annotation.tailrec
  private def poll(u: Unsafe, o: Long): Envelope = {
    val tn = tail
    val n = tn.get
    if (n ne null) {
      if (u.compareAndSwapObject(this, o, tn, n)) {
        val e = n.handle
        n.handle = null // to avoid possible memory leak when queue is empty
        e
      } else poll(u, o)
    } else null
  }

  @annotation.tailrec
  private def count(tn: Node, i: Int): Int = {
    val n = tn.get
    if (n eq null) i
    else count(n, i + 1)
  }
}

private object UQ {
  private val tailOffset = instance.objectFieldOffset(classOf[UQ].getDeclaredField("tail"))
}

private final class Node(var handle: Envelope = null) extends AtomicReference[Node]
