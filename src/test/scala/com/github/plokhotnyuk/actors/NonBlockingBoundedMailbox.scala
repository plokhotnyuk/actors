package com.github.plokhotnyuk.actors

import akka.dispatch._
import akka.actor.{ActorSystem, ActorRef}
import com.typesafe.config.Config
import java.util.concurrent.atomic.AtomicReference

class NonBlockingBoundedMailbox(val bound: Int) extends MailboxType with ProducesMessageQueue[MessageQueue] {
  if (bound <= 0) throw new IllegalArgumentException

  def this(settings: ActorSystem.Settings, config: Config) = this(config.getInt("mailbox-bound"))

  override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue = new NBBQ(bound)
}

private class NBBQ(bound: Int) extends AtomicReference(new NBBQNode) with MessageQueue {
  private var count = 0
  private val tail = new AtomicReference(get)

  override def enqueue(receiver: ActorRef, handle: Envelope): Unit = offer(new NBBQNode(handle))

  override def dequeue(): Envelope = poll()

  override def numberOfMessages: Int = get.count - count

  override def hasMessages: Boolean = numberOfMessages > 0

  override def cleanUp(owner: ActorRef, deadLetters: MessageQueue): Unit =
    while (hasMessages) {
      deadLetters.enqueue(owner, dequeue())
    }

  @annotation.tailrec
  private def offer(n: NBBQNode): Unit = {
    val h = get
    val hc = h.count
    if (hc - count >= bound) throw new OutOfMessageQueueBoundsException
    n.count = hc + 1
    if (compareAndSet(h, n)) h.lazySet(n)
    else offer(n)
  }

  @annotation.tailrec
  private def poll(): Envelope = {
    val t = tail.get
    val n = t.get
    if (n ne null) {
      count = n.count
      if (tail.compareAndSet(t, n)) {
        val e = n.env
        n.env = null // to avoid possible memory leak when queue is empty
        e
      } else poll()
    } else null
  }
}

private class NBBQNode(var env: Envelope = null) extends AtomicReference[NBBQNode] {
  var count: Int = _
}
