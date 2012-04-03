package com.github.plokhotnyuk.actors

import java.util.concurrent.ConcurrentLinkedQueue
import annotation.tailrec
import java.util.concurrent.atomic.{AtomicReference, AtomicBoolean}

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
    processor.send((replyTo, this, msg))
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
    var reply: Any = null
    do {
      reply = mailbox.get
    } while (reply == null)
    reply
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
      val firstProcessor = new EventProcessor(null)
      var processor = firstProcessor
      (1 until num).foreach(i => processor = new EventProcessor(processor))
      firstProcessor.next = processor
      processorDrum.set(firstProcessor)
    }
  }

  @tailrec
  private[actors] def assignProcessor(): EventProcessor = {
    val firstProcessor = processorDrum.get
    val nextProcessor = firstProcessor.next
    if (processorDrum.compareAndSet(firstProcessor, nextProcessor)) {
      nextProcessor
    } else {
      assignProcessor()
    }
  }

  def shutdownPool() {
    synchronized {
      val firstProcessor = processorDrum.get
      var processor = firstProcessor
      do {
        processor.finish()
        processor = processor.next
      } while (processor != firstProcessor)
      processorDrum.set(null)
    }
  }
}

private class EventProcessor(private var next: EventProcessor) extends Thread {
  private[this] val postOffice = new ConcurrentLinkedQueue[(EventBasedActor, EventBasedActor, Any)]
  private[this] val doRun = new AtomicBoolean(true)

  start()

  override def run() {
    while (doRun.get) {
      deliver()
    }
  }

  private[actors] def finish() {
    doRun.set(false)
  }

  private[actors] def send(mail: (EventBasedActor, EventBasedActor, Any)) {
    postOffice.offer(mail)
  }

  @tailrec
  final private[actors] def deliverOthersUntilMine(me: EventBasedActor): Any = {
    val mail = postOffice.poll()
    if (mail != null) {
      val receiver = mail._2
      if (receiver == me) {
        mail._3
      } else {
        receiver.handle(mail._1, mail._3)
        deliverOthersUntilMine(me)
      }
    } else {
      deliverOthersUntilMine(me)
    }
  }

  private[this] def deliver() {
    val mail = postOffice.poll()
    if (mail != null) {
      mail._2.handle(mail._1, mail._3)
    }
  }
}