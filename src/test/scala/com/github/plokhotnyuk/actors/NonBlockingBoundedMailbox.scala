package com.github.plokhotnyuk.actors

import java.util.concurrent.atomic.AtomicReference
import scala.util.control.NoStackTrace
import com.typesafe.config.Config
import akka.actor.{ActorSystem, ActorRef}
import akka.AkkaException
import akka.dispatch._

class NonBlockingBoundedMailbox(bound: Int = Int.MaxValue) extends MailboxType with ProducesMessageQueue[MessageQueue] {
  if (bound <= 0) throw new IllegalArgumentException("Mailbox bound should be greater than 0")

  def this(settings: ActorSystem.Settings, config: Config) = this(config.getInt("mailbox-bound"))

  override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue = new NBBQ(bound)
}

@SerialVersionUID(1L)
case class OutOfMailboxBoundsException(message: String) extends AkkaException(message) with NoStackTrace

private final class NBBQ(bound: Int) extends AtomicReference(new NBBQNode) with MessageQueue {
  private var count = 0
  private val tail = new AtomicReference(get)

  override def enqueue(receiver: ActorRef, handle: Envelope): Unit = offer(new NBBQNode(handle))

  override def dequeue(): Envelope = poll(tail)

  override def numberOfMessages: Int = get.count - count

  override def hasMessages: Boolean = get.count != count

  @annotation.tailrec
  override def cleanUp(owner: ActorRef, deadLetters: MessageQueue): Unit = {
    val env = dequeue()
    if (env ne null) {
      deadLetters.enqueue(owner, env)
      cleanUp(owner, deadLetters)
    }
  }

  @annotation.tailrec
  private def offer(n: NBBQNode): Unit = {
    val h = get
    val hc = h.count
    if (hc - count >= bound) throw new OutOfMailboxBoundsException("Mailbox boundary exceeded")
    else {
      n.count = hc + 1
      if (compareAndSet(h, n)) h.lazySet(n)
      else offer(n)
    }
  }

  @annotation.tailrec
  private def poll(t: AtomicReference[NBBQNode]): Envelope = {
    val tn = t.get
    val n = tn.get
    if (n ne null) {
      count = n.count
      if (t.compareAndSet(tn, n)) {
        val e = n.env
        n.env = null // to avoid possible memory leak when queue is empty
        e
      } else poll(t)
    } else null
  }
}

private class NBBQNode(var env: Envelope = null) extends AtomicReference[NBBQNode] {
  var count: Int = _
}
