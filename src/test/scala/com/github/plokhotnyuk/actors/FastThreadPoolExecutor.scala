package com.github.plokhotnyuk.actors

import java.util.concurrent._
import java.util
import java.util.concurrent.atomic.AtomicInteger
import java.lang.InterruptedException
import scala.annotation.tailrec

/**
 * A high performance implementation of thread pool with fixed number of threads.
 *
 * Based on implementation of ThreadManager from JActor2:
 * https://github.com/laforge49/JActor2/blob/master/jactor-impl/src/main/java/org/agilewiki/jactor/impl/ThreadManagerImpl.java
 *
 * @param threadCount a number of worker threads in pool
 * @param threadFactory a factory to be used to build worker threads
 */
class FastThreadPoolExecutor(threadCount: Int, threadFactory: ThreadFactory) extends AbstractExecutorService {
  private val tasks = new ConcurrentLinkedQueue[Runnable]()
  private val taskRequests = new Semaphore(0)
  private val terminated = new AtomicInteger(threadCount)
  private val closing = new AtomicInteger(0)
  private val threads = (1 to threadCount).map {
    i =>
      val t = threadFactory.newThread(new Runnable() {
        def run() {
          doIgnoringInterrupt(doWork())
          terminated.decrementAndGet()
        }
      })
      t.start()
      t
  }

  def shutdown() {
    shutdownNow()
    awaitTermination(0, TimeUnit.MILLISECONDS)
  }

  def shutdownNow(): util.List[Runnable] = {
    closing.set(1)
    taskRequests.release(threads.size)
    threads.foreach(_.interrupt())
    new util.ArrayList(tasks)
  }

  def isShutdown: Boolean = closing.intValue() != 0

  def isTerminated: Boolean = terminated.intValue() == 0

  def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = {
    val terminator = new Thread() {
      override def run() {
        threads.foreach(t => doIgnoringInterrupt(t.join()))
      }
    }
    terminator.start()
    doIgnoringInterrupt(terminator.join(unit.toMillis(timeout)))
    isTerminated
  }

  def execute(command: Runnable) {
    tasks.offer(command)
    taskRequests.release()
  }

  @tailrec
  private def doWork() {
    if (closing.intValue() == 0) {
      taskRequests.acquire()
      val task = tasks.poll()
      if (task ne null) task.run()
      doWork()
    }
  }

  private def doIgnoringInterrupt(code: => Unit) {
    try {
      code
    } catch {
      case ex: InterruptedException => // ignore
    }
  }
}
