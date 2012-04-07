package com.github.plokhotnyuk.actors

import annotation.tailrec
import java.util.concurrent.atomic.AtomicReference

abstract class EventBasedActor {
  private var processor: EventProcessor = _
  private[this] var sender_ : EventBasedActor = _

  start()

  protected def start() {
    processor = EventProcessor.assignProcessor()
  }

  def !(msg: Any) {
    send(msg, null)
  }

  def send(msg: Any, replyTo: EventBasedActor) {
    processor.send(new Event(replyTo, this, msg))
  }

  def ?(msg: Any): Any = {
    val currentThread = Thread.currentThread()
    if (currentThread.isInstanceOf[EventProcessor]) {
      sendAndReceive(msg, currentThread.asInstanceOf[EventProcessor])
    } else {
      sendAndReceive(msg)
    }
  }

  private[this] def sendAndReceive(msg: Any, otherProcessor: EventProcessor): Any = {
    val replyTo = new EventBasedActor {
      def receive: PartialFunction[Any, Unit] = null // no handler required

      override protected def start() {} // don't assign event processor
    }
    replyTo.processor = otherProcessor
    send(msg, replyTo)
    otherProcessor.deliverOthersUntilMine(replyTo)
  }

  private[this] def sendAndReceive(msg: Any): Any = {
    val mailbox = new AtomicReference[Any](null)
    val replyTo = new EventBasedActor {
      def receive: PartialFunction[Any, Unit] = null // no handler required

      override private[actors] def handle(from: EventBasedActor, msg: Any) {
        mailbox.set(msg)
      }
    }
    send(msg, replyTo)
    while (mailbox.get == null) {}
    mailbox.get
  }

  protected def receive: PartialFunction[Any, Unit]

  protected def sender: EventBasedActor = sender_

  protected def reply(msg: Any) {
    sender_.send(msg, this)
  }

  private[actors] def handle(from: EventBasedActor, msg: Any) {
    sender_ = from
    val handler = receive
    if (handler.isDefinedAt(msg)) {
      handler.apply(msg)
    }
  }
}

object EventProcessor {
  private[this] val processorDrum = new AtomicReference[EventProcessor]

  def initPool(num: Int) {
    synchronized {
      val first = new EventProcessor(null)
      var current = first
      (1 until num).foreach(i => current = new EventProcessor(current))
      first.next = current
      processorDrum.set(first)
    }
  }

  @tailrec
  private[actors] def assignProcessor(): EventProcessor = {
    val first = processorDrum.get
    val current = first.next
    if (processorDrum.compareAndSet(first, current)) {
      current
    } else {
      assignProcessor()
    }
  }

  def shutdownPool() {
    synchronized {
      val first = processorDrum.get
      var current = first
      do {
        current.finish()
        current = current.next
      } while (current != first)
      processorDrum.set(null)
    }
  }
}

private class EventProcessor(private var next: EventProcessor) extends Thread {
  private[this] var tail = new Event(null, null, null)
  @volatile private[this] var doRun = true
  private[this] val head = new AtomicReference[Event](tail)

  start()

  override def run() {
    while (doRun) {
      deliver()
    }
  }

  private[actors] def finish() {
    doRun = false
  }

  private[actors] def send(event: Event) {
    head.getAndSet(event).next = event
  }

  @tailrec
  final private[actors] def deliverOthersUntilMine(me: EventBasedActor): Any = {
    val event = tail.next
    if (event != null) {
      tail = event
      if (event.receiver == me) {
        event.msg
      } else {
        event.receiver.handle(event.sender, event.msg)
        deliverOthersUntilMine(me)
      }
    } else {
      deliverOthersUntilMine(me)
    }
  }

  private[this] def deliver() {
    val event = tail.next
    if (event != null) {
      tail = event
      event.receiver.handle(event.sender, event.msg)
    }
  }
}

private[actors] class Event(val sender: EventBasedActor, val receiver: EventBasedActor, val msg: Any) {
  @volatile var next: Event = _
}