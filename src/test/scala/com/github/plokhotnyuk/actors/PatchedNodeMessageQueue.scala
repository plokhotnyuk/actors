package com.github.plokhotnyuk.actors

import akka.dispatch.{MessageQueue, Envelope}
import akka.actor.ActorRef
import scala.annotation.tailrec

class PatchedNodeMessageQueue extends PatchedAbstractNodeQueue[Envelope] with MessageQueue {
  final def enqueue(receiver: ActorRef, handle: Envelope) {
    add(handle)
  }

  final def dequeue(): Envelope = poll()

  final def numberOfMessages: Int = count()

  final def hasMessages: Boolean = !isEmpty

  @tailrec final def cleanUp(owner: ActorRef, deadLetters: MessageQueue) {
    val envelope = dequeue()
    if (envelope ne null) {
      deadLetters.enqueue(owner, envelope)
      cleanUp(owner, deadLetters)
    }
  }
}
