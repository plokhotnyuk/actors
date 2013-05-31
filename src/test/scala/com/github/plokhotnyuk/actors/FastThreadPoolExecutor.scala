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
 * @param handler the handler for internal worker threads that will be called
 *                in case of unrecoverable errors encountered while executing tasks.
 */
class FastThreadPoolExecutor(threadCount: Int = Runtime.getRuntime.availableProcessors(),
                             threadFactory: ThreadFactory = new ThreadFactory() {
                               def newThread(r: Runnable): Thread = new Thread(r) {
                                 setDaemon(true) // is it good reason: to avoid stalls on app end in case of missed shutdown call?
                               }
                             },
                             handler: Thread.UncaughtExceptionHandler = null) extends AbstractExecutorService {

  private val closing = new AtomicInteger(0)
  private val taskRequests = new Semaphore(0)
  private val tasks = new ConcurrentLinkedQueue[Runnable]()
  private val threadTerminations = new CountDownLatch(threadCount)
  private val threads = {
    val tf = threadFactory // to avoid creating of field for the threadFactory constructor param
    (1 to threadCount).map {
      _ =>
        tf.newThread(new Runnable() {
          def run() {
            doWork()
          }
        })
    }
  }

  threads.foreach(_.start())

  def shutdown() {
    shutdownNow()
    awaitTermination(0, TimeUnit.MILLISECONDS)
  }

  def shutdownNow(): util.List[Runnable] = {
    closing.set(1)
    taskRequests.release(threads.size)
    threads.filter(_ ne Thread.currentThread()).foreach(_.interrupt()) // don't interrupt worker thread due call in task
    drainRemainingTasks(new util.LinkedList[Runnable]())
  }

  def isShutdown: Boolean = closing.intValue != 0

  def isTerminated: Boolean = threadTerminations.getCount == 0

  def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = {
    if (threads.exists(_ eq Thread.currentThread())) threadTerminations.countDown() // don't hang up due call in task
    threadTerminations.await(timeout, unit)
  }

  def execute(command: Runnable) {
    tasks.offer(command) // is it need to check the closing flag?
    taskRequests.release()
  }

  @tailrec
  private def doWork() {
    try {
      taskRequests.acquire()
      val t = tasks.poll()
      if (t ne null) t.run()
    } catch {
      case ex: InterruptedException =>
        threadTerminations.countDown()
        return
      case ex: Throwable =>
        if (handler != null) handler.uncaughtException(Thread.currentThread(), ex) // is it safe error handling
        else ex.printStackTrace()
    }
    doWork()
  }

  @tailrec
  private def drainRemainingTasks(ts: util.List[Runnable]): util.List[Runnable] = {
    val t = tasks.poll()
    if (t eq null) ts
    else {
      ts.add(t)
      drainRemainingTasks(ts)
    }
  }
}