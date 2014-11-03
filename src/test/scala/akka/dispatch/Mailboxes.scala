package akka.dispatch

import akka.actor.{InternalActorRef, ActorRef, ActorSystem, DeadLetter}
import akka.util.Unsafe
import com.typesafe.config.Config
import java.util.concurrent.atomic.AtomicReference

class NonBlockingBoundedMailbox(capacity: Int = Int.MaxValue) extends MailboxType with ProducesMessageQueue[MessageQueue] {
  require(capacity > 0, "Mailbox capacity should be greater than 0")

  def this(settings: ActorSystem.Settings, config: Config) = this(config.getInt("mailbox-capacity"))

  override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue = new NBBQ(capacity)
}

private class NBBQ(capacity: Int) extends AtomicReference(new NodeWithCount) with MessageQueue with MultipleConsumerSemantics {
  @volatile private var tail: NodeWithCount = get

  override def enqueue(receiver: ActorRef, handle: Envelope): Unit =
    if (!offer(new NodeWithCount(handle))) onOverflow(receiver, handle)

  override def dequeue(): Envelope = poll()

  override def numberOfMessages: Int = Math.min(capacity, Math.max(0, get.count - tail.count))

  override def hasMessages: Boolean = {
    val tn = tail
    (tn.get ne null) || (tn ne get)
  }

  override def cleanUp(owner: ActorRef, deadLetters: MessageQueue): Unit = drain(owner, deadLetters)

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
  private def poll(): Envelope = {
    val tn = tail
    val n = tn.get
    if (n ne null) {
      if (Unsafe.instance.compareAndSwapObject(this, NBBQ.tailOffset, tn, n)) {
        val e = n.handle
        n.handle = null // to avoid possible memory leak when queue is empty
        e
      } else poll()
    } else if (tn ne get) poll()
    else null
  }

  @annotation.tailrec
  private def drain(owner: ActorRef, deadLetters: MessageQueue): Unit = {
    val e = dequeue()
    if (e ne null) {
      deadLetters.enqueue(owner, e)
      drain(owner, deadLetters)
    }
  }
}

private object NBBQ {
  private val tailOffset = Unsafe.instance.objectFieldOffset(classOf[NBBQ].getDeclaredField("tail"))
}

private class NodeWithCount(var handle: Envelope = null) extends AtomicReference[NodeWithCount] {
  var count: Int = _
}

class UnboundedMailbox2 extends MailboxType with ProducesMessageQueue[MessageQueue] {
  def this(settings: ActorSystem.Settings, config: Config) = this()

  override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue = new UQ
}

private class UQ extends AtomicReference(new Node) with MessageQueue with MultipleConsumerSemantics {
  @volatile private var tail: Node = get

  override def enqueue(receiver: ActorRef, handle: Envelope): Unit = {
    val n = new Node(handle)
    getAndSet(n).lazySet(n)
  }

  override def dequeue(): Envelope = poll()

  override def numberOfMessages: Int = count(tail, 0, Int.MaxValue)

  override def hasMessages: Boolean = {
    val tn = tail
    (tn.get ne null) || (tn ne get)
  }

  override def cleanUp(owner: ActorRef, deadLetters: MessageQueue): Unit = drain(owner, deadLetters)

  @annotation.tailrec
  private def poll(): Envelope = {
    val tn = tail
    val n = tn.get
    if (n ne null) {
      if (Unsafe.instance.compareAndSwapObject(this, UQ.tailOffset, tn, n)) {
        val e = n.handle
        n.handle = null // to avoid possible memory leak when queue is empty
        e
      } else poll()
    } else if (tn ne get) poll()
    else null
  }

  @annotation.tailrec
  private def count(tn: Node, i: Int, l: Int): Int = {
    val n = tn.get
    if (i == l) i
    else if (n ne null) count(n, i + 1, l)
    else if (tn ne get) count(tn, i, l)
    else i
  }

  @annotation.tailrec
  private def drain(owner: ActorRef, deadLetters: MessageQueue): Unit = {
    val e = dequeue()
    if (e ne null) {
      deadLetters.enqueue(owner, e)
      drain(owner, deadLetters)
    }
  }
}

private object UQ {
  private val tailOffset = Unsafe.instance.objectFieldOffset(classOf[UQ].getDeclaredField("tail"))
}

private class Node(var handle: Envelope = null) extends AtomicReference[Node]
