package com.github.plokhotnyuk.actors

import com.typesafe.config.Config
import java.util.concurrent.atomic.AtomicReference
import akka.dispatch._
import akka.actor.{ActorRef, ActorContext, ActorSystem}

/**
 * CAUTION!!!
 * Should not be used with dispatchers that use simultaneous call of dequeue() in different threads,
 * like BalancingDispatcher
 *
 * Based on non-intrusive MPSC node-based queue, described by Dmitriy Vyukov:
 * http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue
 */
case class UnboundedMailbox2() extends MailboxType {
  def this(settings: ActorSystem.Settings, config: Config) = this()

  final override def create(owner: Option[ActorContext]): MessageQueue = new MPSCQueue2()
}

class MPSCQueue2 extends MessageQueue {
  private[this] val tail = new AtomicReference[EnvelopeNode](new EnvelopeNode(null))
  private[this] val head = new AtomicReference[EnvelopeNode](tail.get)

  def enqueue(receiver: ActorRef, handle: Envelope) {
    val node = new EnvelopeNode(handle)
    head.getAndSet(node).set(node)
  }

  def dequeue(): Envelope = {
    val next = tail.get.get
    if (next ne null) {
      tail.lazySet(next)
      next.envelope
    } else null
  }

  def numberOfMessages: Int = {
    var n = 0
    var next = tail.get.get
    while (next ne null) {
      next = next.get
      n += 1
    }
    n
  }

  def hasMessages: Boolean = tail.get.get ne null

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