package com.github.plokhotnyuk.actors

import annotation.tailrec
import java.util.concurrent.atomic.AtomicReference

abstract class ThreadBasedActor {
  private[this] var sender_ : ThreadBasedActor = _
  private[this] var tail = new Mail(null, null)
  @volatile private[this] var doRun = true
  private[this] val head = new AtomicReference[Mail](tail)

  start()

  def !(msg: Any) {
    send(msg, null)
  }

  def send(msg: Any, replyTo: ThreadBasedActor) {
    val mail = new Mail(replyTo, msg)
    head.getAndSet(mail).next = mail
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
    doRun = false
  }

  protected def start() {
    new Thread() {
      override def run() {
        handleMessages()
      }
    }.start()
  }

  private[this] def handleMessages() {
    while (doRun) {
      handle(message())
    }
  }

  @tailrec
  private def message(): Any = {
    val mail = tail.next
    if (mail != null) {
      tail = mail
      sender_ = mail.sender
      mail.msg
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

private[actors] class Mail(val sender: ThreadBasedActor, val msg: Any) {
  @volatile var next: Mail = _
}