package com.github.plokhotnyuk.actors

import scala.concurrent._
import java.util.concurrent.Executors

/**
 * Copy of a simplistic actor implementation from:
 * http://stackoverflow.com/a/27657976/228843
 */
trait SimplisticActor[T] {
  implicit val context: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  def receive: T => Unit

  def !(m: T) = Future(receive(m))
}
