package akka.dispatch

import java.util.concurrent.atomic.AtomicReference
import com.typesafe.config.Config
import akka.actor.{InternalActorRef, ActorRef, ActorSystem}
import akka.actor.DeadLetter

class NonBlockingBoundedMailbox(capacity: Int = Int.MaxValue) extends MailboxType with ProducesMessageQueue[MessageQueue] {
  if (capacity <= 0) throw new IllegalArgumentException("Mailbox capacity should be greater than 0")

  def this(settings: ActorSystem.Settings, config: Config) = this(config.getInt("mailbox-capacity"))

  override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue = new NBBQ(capacity)
}

private class NBBQ(capacity: Int) extends AtomicReference(new NBBQNode) with MessageQueue with MultipleConsumerSemantics {
  private val tail = new AtomicReference(get)

  override def enqueue(receiver: ActorRef, handle: Envelope): Unit =
    if (!offer(new NBBQNode(handle))) {
      receiver.asInstanceOf[InternalActorRef].provider.deadLetters
        .tell(DeadLetter(handle.message, handle.sender, receiver), handle.sender)
    }

  override def dequeue(): Envelope = poll(tail)

  override def numberOfMessages: Int = get.count - tail.get.count

  override def hasMessages: Boolean = get ne tail.get

  override def cleanUp(owner: ActorRef, deadLetters: MessageQueue): Unit = {
    var e = dequeue()
    while (e ne null) {
      deadLetters.enqueue(owner, e)
      e = dequeue()
    }
  }

  @annotation.tailrec
  private def offer(n: NBBQNode): Boolean = {
    val tc = tail.get.count
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

  @annotation.tailrec
  private def poll(t: AtomicReference[NBBQNode]): Envelope = {
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
}

private class NBBQNode(var handle: Envelope = null) extends AtomicReference[NBBQNode] {
  var count: Int = _
}
