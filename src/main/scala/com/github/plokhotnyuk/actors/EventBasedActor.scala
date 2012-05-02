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

      override protected def start() {
        processor = otherProcessor
      }
    }
    send(msg, replyTo)
    otherProcessor.deliverOthersUntilMine(replyTo)
  }

  private[this] def sendAndReceive(msg: Any): Any = {
    val replyTo = new TempEventBasedActor
    send(msg, replyTo)
    replyTo.message()
  }

  protected def receive: PartialFunction[Any, Unit]

  def handleError: PartialFunction[Throwable, Unit] = {
    case _@ex => ex.printStackTrace()
  }

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

private[actors] class TempEventBasedActor extends EventBasedActor with BackOff {
  @volatile private[this] var mailbox: Any = _

  def receive: PartialFunction[Any, Unit] = null // no handler required

  override private[actors] def handle(from: EventBasedActor, msg: Any) {
    mailbox = msg
  }

  private[actors] def message() {
    while (mailbox == null) {
      backOff()
    }
    mailbox
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

/**
 * Based on non-intrusive MPSC node-based queue, described by Dmitriy Vyukov:
 * http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue
 */
private class EventProcessor(private var next: EventProcessor) extends Thread with BackOff {
  var t0, t1, t2, t3, t4, t5: Long = _
  @volatile private[this] var doRun = 1L
  private[this] var tail = new Event(null, null, null)
  var h0, h1, h2, h3, h4, h5, h6: Long = _
  private[this] val head = new AtomicReference[Event](tail)

  start()

  override def run() {
    while (doRun != 0L) {
      try {
        while (doRun != 0L) {
          deliver()
        }
      } catch {
        tail.receiver.handleError
      }
    }
  }

  private[actors] def finish() {
    doRun = 0L
  }

  private[actors] def send(event: Event) {
    head.getAndSet(event).lazySet(event)
  }

  @tailrec
  final private[actors] def deliverOthersUntilMine(me: EventBasedActor): Any = {
    val event = tail.get
    if (event ne null) {
      tail = event
      if (event.receiver == me) {
        event.msg
      } else {
        try {
          event.receiver.handle(event.sender, event.msg)
        } catch {
          event.receiver.handleError
        }
        deliverOthersUntilMine(me)
      }
    } else {
      backOff()
      deliverOthersUntilMine(me)
    }
  }

  private[this] def deliver() {
    val event = tail.get
    if (event ne null) {
      tail = event
      event.receiver.handle(event.sender, event.msg)
    } else {
      backOff()
    }
  }
}

private[actors] class Event(val sender: EventBasedActor, val receiver: EventBasedActor, val msg: Any) extends AtomicReference[Event]