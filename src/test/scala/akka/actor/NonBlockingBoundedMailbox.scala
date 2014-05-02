package akka.actor

import java.util.concurrent.atomic.AtomicReference
import com.typesafe.config.Config
import akka.dispatch._

class NonBlockingBoundedMailbox(bound: Int = Int.MaxValue) extends MailboxType with ProducesMessageQueue[MessageQueue] {
  if (bound <= 0) throw new IllegalArgumentException("Mailbox bound should be greater than 0")

  def this(settings: ActorSystem.Settings, config: Config) = this(config.getInt("mailbox-bound"))

  override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue = new NBBQ(bound)
}

private class NBBQ(bound: Int) extends AtomicReference(new NBBQNode) with MessageQueue with MultipleConsumerSemantics {
  private val tail = new AtomicReference(get)

  override def enqueue(receiver: ActorRef, handle: Envelope): Unit =
    if (!offer(new NBBQNode(handle), 0)) {
      receiver.asInstanceOf[InternalActorRef].provider.deadLetters
        .tell(DeadLetter(handle.message, handle.sender, receiver), handle.sender)
    }

  override def dequeue(): Envelope = poll(tail, 0)

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
  private def offer(n: NBBQNode, i: Int): Boolean = {
    val tc = tail.get.count
    val h = get
    val hc = h.count
    if (hc - tc < bound) {
      n.count = hc + 1
      if (compareAndSet(h, n)) {
        h.lazySet(n)
        true
      } else {
        backOff(i)
        offer(n, i + 1)
      }
    } else false
  }

  private def backOff(i: Int): Unit = {
    val mask = NBBQ.mask
    if ((i & mask) == mask) Thread.`yield`()
  }

  @annotation.tailrec
  private def poll(t: AtomicReference[NBBQNode], i: Int): Envelope = {
    val tn = t.get
    val n = tn.get
    if (n ne null) {
      if (t.compareAndSet(tn, n)) {
        val e = n.handle
        n.handle = null // to avoid possible memory leak when queue is empty
        e
      } else {
        backOff(i)
        poll(t, i + 1)
      }
    } else null
  }
}

private object NBBQ {
  val mask = Integer.highestOneBit(Runtime.getRuntime.availableProcessors()) - 1
}

private class NBBQNode(var handle: Envelope = null) extends AtomicReference[NBBQNode] {
  var count: Int = _
}
