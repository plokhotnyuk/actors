package com.github.plokhotnyuk.actors

import annotation.tailrec
import java.util.concurrent.atomic.AtomicReference

/**
 * Using of non-intrusive MPSC node-based queue, described by Dmitriy Vyukov:
 * http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue
 */
abstract class ThreadBasedActor extends BackOff {
  var t0, t1, t2, t3, t4, t5: Long = _
  @volatile private[this] var doRun = 1L
  private[this] var tail = new Mail(null, null)
  var h0, h1, h2, h3, h4, h5, h6: Long = _
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

  def exit() {
    doRun = 0L
  }

  protected def receive: PartialFunction[Any, Unit]

  def handleError: PartialFunction[Throwable, Unit] = {
    case _@ex => ex.printStackTrace()
  }

  protected def sender: ThreadBasedActor = tail.sender

  protected def reply(msg: Any) {
    tail.sender.send(msg, this)
  }

  protected def start() {
    new Thread() {
      override def run() {
        handleMessages()
      }
    }.start()
  }

  private[this] def handleMessages() {
    while (doRun != 0L) {
      try {
        while (doRun != 0L) {
          receive.apply(message())
        }
      } catch {
        handleError
      }
    }
  }

  @tailrec
  private def message(): Any = {
    val mail = tail.next
    if (mail ne null) {
      tail = mail
      mail.msg
    } else {
      backOff()
      message()
    }
  }
}

private[actors] class Mail(val sender: ThreadBasedActor, val msg: Any) {
  @volatile var next: Mail = _
}