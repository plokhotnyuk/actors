package akka.dispatch

import akka.actor.{InternalActorRef, ActorRef, ActorSystem, DeadLetter}
import com.typesafe.config.Config
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

class NonBlockingBoundedMailbox(capacity: Int = Int.MaxValue) extends MailboxType with ProducesMessageQueue[MessageQueue] {
  if (capacity <= 0) throw new IllegalArgumentException("Mailbox capacity should be greater than 0")

  def this(settings: ActorSystem.Settings, config: Config) = this(config.getInt("mailbox-capacity"))

  override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue = new NBBQ(capacity)
}

private class NBBQ(capacity: Int) extends AtomicInteger with MessageQueue with MultipleConsumerSemantics {
  private val head = new AtomicReference(new QNode)
  private val tail = new AtomicReference(head.get)

  override def enqueue(receiver: ActorRef, handle: Envelope): Unit = {
    val n = new QNode(handle)
    val h = head
    if (capacity > getAndIncrement()) h.getAndSet(n).set(n)
    else {
      getAndDecrement()
      overflow(receiver, handle)
    }
  }

  override def dequeue(): Envelope = poll(tail)

  override def numberOfMessages: Int = get

  override def hasMessages: Boolean = 0 < get

  override def cleanUp(owner: ActorRef, deadLetters: MessageQueue): Unit = {
    var e = dequeue()
    while (e ne null) {
      deadLetters.enqueue(owner, e)
      e = dequeue()
    }
  }

  private def overflow(a: ActorRef, e: Envelope): Unit =
    a.asInstanceOf[InternalActorRef].provider.deadLetters.tell(DeadLetter(e.message, e.sender, a), e.sender)

  @annotation.tailrec
  private def poll(t: AtomicReference[QNode]): Envelope = {
    val tn = t.get
    val n = tn.get
    if (n ne null) {
      if (t.compareAndSet(tn, n)) {
        getAndDecrement()
        val e = n.handle
        n.handle = null // to avoid possible memory leak when queue is empty
        e
      } else poll(t)
    } else null
  }
}

class UnboundedMailbox2 extends MailboxType with ProducesMessageQueue[MessageQueue] {
  def this(settings: ActorSystem.Settings, config: Config) = this()

  override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue = new UQ
}

private class UQ extends AtomicReference(new QNode) with MessageQueue with MultipleConsumerSemantics {
  private val tail = new AtomicReference(get)

  override def enqueue(receiver: ActorRef, handle: Envelope): Unit = {
    val n = new QNode(handle)
    getAndSet(n).set(n)
  }

  override def dequeue(): Envelope = poll(tail)

  override def numberOfMessages: Int = count(tail.get, 0)

  override def hasMessages: Boolean = null ne tail.get.get

  override def cleanUp(owner: ActorRef, deadLetters: MessageQueue): Unit = {
    var e = dequeue()
    while (e ne null) {
      deadLetters.enqueue(owner, e)
      e = dequeue()
    }
  }

  @annotation.tailrec
  private def poll(t: AtomicReference[QNode]): Envelope = {
    val tn = t.get
    val n = tn.get
    if (n ne null) {
      if (t.compareAndSet(tn, n)) {
        val e = n.handle
        n.handle = null // to avoid possible memory leak when queue is empty
        e
      } else poll(t)
    } else null
  }

  @annotation.tailrec
  private def count(tn: AtomicReference[QNode], i: Int): Int = {
    val n = tn.get
    if (n eq null) i
    else count(n, i + 1)
  }
}

private class QNode(var handle: Envelope = null) extends AtomicReference[QNode]
