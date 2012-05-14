package com.github.plokhotnyuk.actors

import com.typesafe.config.Config
import java.util.concurrent.atomic.AtomicReference
import akka.dispatch._
import akka.actor.{ActorRef, ActorContext, ActorSystem}

case class UnboundedMailbox2() extends MailboxType {
  def this(settings: ActorSystem.Settings, config: Config) = this()

  final override def create(owner: Option[ActorContext]): MessageQueue = new MPSCQueue2()
}

class MPSCQueue2 extends MessageQueue {
  @volatile private[this] var tail = new EnvelopeNode(null)
  private[this] val head = new AtomicReference[EnvelopeNode](tail)

  def enqueue(receiver: ActorRef, handle: Envelope) {
    val node = new EnvelopeNode(handle)
    head.getAndSet(node).set(node)
  }

  def dequeue(): Envelope = synchronized {
    val next = tail.get
    if (next ne null) {
      tail = next
      next.envelope
    } else null
  }

  def numberOfMessages: Int = {
    var n = 0
    var next = tail.get
    while (next ne null) {
      next = next.get
      n += 1
    }
    n
  }

  def hasMessages: Boolean = tail.get ne null

  def cleanUp(owner: ActorContext, deadLetters: MessageQueue) {
    if (hasMessages) {
      var envelope = dequeue()
      while (envelope ne null) {
        deadLetters.enqueue(owner.self, envelope)
        envelope = dequeue()
      }
    }
  }
}

class EnvelopeNode(val envelope: Envelope) extends AtomicReference[EnvelopeNode]