package com.github.plokhotnyuk.actors

import annotation.tailrec
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

trait ThreadBasedActor {
  private[this] val mailbox = new ConcurrentLinkedQueue[(Any, ThreadBasedActor)]
  private[this] val doRun = new AtomicBoolean(true)
  private[this] var sender_ : ThreadBasedActor = _

  start()

  def !(msg: Any) {
    send(msg, null)
  }

  def send(msg: Any, replyTo: ThreadBasedActor) {
    mailbox.offer((msg, replyTo))
  }

  def ?(msg: Any): Any = {
    val replyTo = new ThreadBasedActor {
      def receive: PartialFunction[Any, Unit] = null // no handler required

      override def start() {} // don't start thread
    }
    send(msg, replyTo)
    replyTo.message()
  }

  protected def receive: PartialFunction[Any, Unit]

  protected def sender: ThreadBasedActor = sender_

  protected def reply(msg: Any) {
    sender_.send(msg, this)
  }

  protected def exit() {
    doRun.set(false)
  }

  protected def start() {
    new Thread() {
      override def run() {
        handleMessages()
      }
    }.start()
  }

  private[this] def handleMessages() {
    while (doRun.get) {
      handle(message())
    }
  }

  @tailrec
  private def message(): Any = {
    val mail = mailbox.poll()
    if (mail != null) {
      sender_ = mail._2
      mail._1
    } else {
      message()
    }
  }

  private[this] def handle(msg: Any) {
    val handler = receive
    if (handler.isDefinedAt(msg)) {
      handler.apply(msg)
    }
  }
}
