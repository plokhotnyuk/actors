package com.github.plokhotnyuk.actors

import akka.dispatch.{MessageQueue, MailboxType}
import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.config.Config

/**
 * SingleConsumerOnlyUnboundedMailbox is a high-performance, multiple producer—single consumer, unbounded MailboxType,
 * the only drawback is that you can't have multiple consumers,
 * which rules out using it with BalancingDispatcher for instance.
 */
case class PatchedSingleConsumerOnlyUnboundedMailbox() extends MailboxType {
  def this(settings: ActorSystem.Settings, config: Config) = this()

  final override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue = try {
    new PatchedNodeMessageQueue()
  } catch {
    case t: Throwable => t.printStackTrace(); null
  }
}
