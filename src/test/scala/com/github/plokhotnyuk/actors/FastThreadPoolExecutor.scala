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
  private val closing = new AtomicInteger(0)
  private val taskRequests = new Semaphore(0)
  private val tasks = new ConcurrentLinkedQueue[Runnable]()
  private val terminated = new CountDownLatch(threadCount)
  private val threads = (1 to threadCount).map {
    i =>
      threadFactory.newThread(new Runnable() {
        def run() {
          try {
            doWork()
          } catch {
            case ex: InterruptedException => // ignore
          } finally {
            terminated.countDown()
          }
        }
      })
  }

  threads.foreach(_.start())

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

  def isShutdown: Boolean = !isRunning

  def isTerminated: Boolean = terminated.getCount == 0

  def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = terminated.await(timeout, unit)

  def execute(command: Runnable) {
    tasks.offer(command)
    taskRequests.release()
  }

  @tailrec
  private def doWork() {
    if (isRunning) {
      taskRequests.acquire()
      val task = tasks.poll()
      if (task ne null) task.run()
      doWork()
    }
  }

  private def isRunning: Boolean = closing.intValue() == 0
}