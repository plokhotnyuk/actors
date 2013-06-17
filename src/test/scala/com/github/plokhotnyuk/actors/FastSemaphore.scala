package com.github.plokhotnyuk.actors

import java.util.concurrent.locks.AbstractQueuedSynchronizer
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec

class FastSemaphore {
  private val state = new AtomicInteger()
  private val sync = new AbstractQueuedSynchronizer() {
    final protected override def tryReleaseShared(releases: Int): Boolean = FastSemaphore.this.tryReleaseShared(releases)

    final protected override def tryAcquireShared(acquires: Int): Int = FastSemaphore.this.tryAcquireShared(acquires)
  }

  def acquire() {
    sync.acquireSharedInterruptibly(1)
  }

  def release() {
    sync.releaseShared(1)
  }

  def reducePermit() {
    tryReleaseShared(-1)
  }

  def availablePermits(): Int = state.get

  private def tryReleaseShared(releases: Int): Boolean = {
    state.getAndAdd(releases)
    true
  }

  @tailrec
  private def tryAcquireShared(acquires: Int): Int = {
    val available = state.get
    val remaining = available - acquires
    if (remaining < 0 || state.compareAndSet(available, remaining)) remaining else tryAcquireShared(acquires)
  }
}