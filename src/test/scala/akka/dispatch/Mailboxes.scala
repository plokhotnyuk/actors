package akka.dispatch

import java.util.concurrent.atomic.AtomicReference
import com.typesafe.config.Config
import akka.actor.{InternalActorRef, ActorRef, ActorSystem, DeadLetter}

class NonBlockingBoundedMailbox(capacity: Int = Int.MaxValue) extends MailboxType with ProducesMessageQueue[MessageQueue] {
  if (capacity <= 0) throw new IllegalArgumentException("Mailbox capacity should be greater than 0")

  def this(settings: ActorSystem.Settings, config: Config) = this(config.getInt("mailbox-capacity"))

  override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue = new NBBQ(capacity)
}

private final class NBBQ(capacity: Int) extends AtomicReference(new QNodeWithCount) with MessageQueue with MultipleConsumerSemantics {
  private val tail = new TailWithCount(get)

  override def enqueue(receiver: ActorRef, handle: Envelope): Unit =
    if (!offer(new QNodeWithCount(handle), tail.get.count)) {
      receiver.asInstanceOf[InternalActorRef].provider.deadLetters
        .tell(DeadLetter(handle.message, handle.sender, receiver), handle.sender)
    }

  override def dequeue(): Envelope = tail.poll()

  override def numberOfMessages: Int = get.count - tail.get.count

  override def hasMessages: Boolean = tail.hasMessages

  override def cleanUp(owner: ActorRef, deadLetters: MessageQueue): Unit = tail.cleanUp(owner, deadLetters)

  @annotation.tailrec
  private def offer(n: QNodeWithCount, tc: Int): Boolean = {
    val h = get
    val hc = h.count
    if (hc - tc < capacity) {
      n.count = hc + 1
      if (compareAndSet(h, n)) {
        h.set(n)
        true
      } else offer(n, tc)
    } else {
      val ntc = tail.get.count
      if (tc == ntc) false
      else offer(n, ntc)
    }
  }
}

private final class TailWithCount(tn: QNodeWithCount) extends AtomicReference(tn) {
  @annotation.tailrec
  def poll(): Envelope = {
    val tn = get
    val n = tn.get
    if (n ne null) {
      if (compareAndSet(tn, n)) {
        val e = n.handle
        n.handle = null // to avoid possible memory leak when queue is empty
        e
      } else poll()
    } else null
  }

  def hasMessages: Boolean = get.get ne null

  def cleanUp(owner: ActorRef, deadLetters: MessageQueue): Unit = {
    var e = poll()
    while (e ne null) {
      deadLetters.enqueue(owner, e)
      e = poll()
    }
  }
}

private final class QNodeWithCount(var handle: Envelope = null) extends AtomicReference[QNodeWithCount] {
  var count: Int = _
}

class UnboundedMailbox2 extends MailboxType with ProducesMessageQueue[MessageQueue] {
  def this(settings: ActorSystem.Settings, config: Config) = this()

  override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue = new UQ
}

private final class UQ extends AtomicReference(new QNode) with MessageQueue with MultipleConsumerSemantics {
  private val tail = new Tail(get)

  override def enqueue(receiver: ActorRef, handle: Envelope): Unit = {
    val n = new QNode(handle)
    getAndSet(n).set(n)
  }

  override def dequeue(): Envelope = tail.poll()

  override def numberOfMessages: Int = count(tail.get, 0)

  override def hasMessages: Boolean = tail.hasMessages

  override def cleanUp(owner: ActorRef, deadLetters: MessageQueue): Unit = tail.cleanUp(owner, deadLetters)

  @annotation.tailrec
  private def count(tn: AtomicReference[QNode], i: Int): Int = {
    val n = tn.get
    if (n eq null) i
    else count(n, i + 1)
  }
}

private final class QNode(var handle: Envelope = null) extends AtomicReference[QNode]

private final class Tail(tn: QNode) extends AtomicReference(tn) {
  @annotation.tailrec
  def poll(): Envelope = {
    val tn = get
    val n = tn.get
    if (n ne null) {
      if (compareAndSet(tn, n)) {
        val e = n.handle
        n.handle = null // to avoid possible memory leak when queue is empty
        e
      } else poll()
    } else null
  }

  def hasMessages: Boolean = get.get ne null

  def cleanUp(owner: ActorRef, deadLetters: MessageQueue): Unit = {
    var e = poll()
    while (e ne null) {
      deadLetters.enqueue(owner, e)
      e = poll()
    }
  }
}