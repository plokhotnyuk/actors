package com.github.plokhotnyuk.actors

import java.util.concurrent._
import java.util.concurrent.atomic.{AtomicReference, AtomicInteger}
import java.lang.InterruptedException
import scala.annotation.tailrec
import java.util.concurrent.locks.LockSupport

/**
 * A high performance implementation of thread pool with fixed number of threads.
 *
 * Implementation of task queue based on non-intrusive MPSC node-based queue, described by Dmitriy Vyukov:
 * http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue
 *
 * @param threadCount a number of worker threads in pool
 * @param threadFactory a factory to be used to build worker threads
 * @param handler the handler for internal worker threads that will be called
 *                in case of unrecoverable errors encountered while executing tasks.
 */
class FastThreadPoolExecutor(threadCount: Int = Runtime.getRuntime.availableProcessors(),
                             threadFactory: ThreadFactory = new ThreadFactory() {
                               def newThread(r: Runnable): Thread = new Thread(r) {
                                 setDaemon(true) // is it good reason: "to avoid stalls on app end in case of missed shutdown call"?
                               }
                             },
                             handler: Thread.UncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
                               def uncaughtException(t: Thread, e: Throwable) {
                                 e.printStackTrace() // is it safe default implementation?
                               }
                             }) extends AbstractExecutorService {
  private val closing = new AtomicInteger(0)
  private val taskHead = new AtomicReference[TaskNode](new TaskNode())
  private val taskTail = new AtomicReference[TaskNode](taskHead.get)
  private val terminations = new CountDownLatch(threadCount)
  private val threads = {
    val tf = threadFactory // to avoid creating of field for the threadFactory constructor param
    val c = closing  // to avoid long field names
    val tt = taskTail
    val h = handler
    val t = terminations
    (1 to threadCount).map(_ => tf.newThread(new Worker(c, tt, h, t)))
  }
  threads.foreach(_.start())

  def shutdown() {
    shutdownNow()
    awaitTermination(0, TimeUnit.MILLISECONDS)
  }

  def shutdownNow(): java.util.List[Runnable] = {
    closing.set(1)
    threads.filter(_ ne Thread.currentThread()).foreach(_.interrupt()) // don't interrupt worker thread due call in task
    drainRemainingTasks(new java.util.LinkedList[Runnable]())
  }

  def isShutdown: Boolean = closing.get != 0

  def isTerminated: Boolean = terminations.getCount == 0

  def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = {
    if (threads.exists(_ eq Thread.currentThread())) terminations.countDown() // don't hang up due call in task
    terminations.await(timeout, unit)
  }

  def execute(task: Runnable) {
    if (isShutdown) throw new IllegalStateException("Cannot execute in terminating/shutdown state")
    if (task eq null) throw new NullPointerException
    val n = new TaskNode(task)
    taskHead.getAndSet(n).lazySet(n)
  }

  @tailrec
  private def drainRemainingTasks(ts: java.util.List[Runnable]): java.util.List[Runnable] = {
    val tn = taskTail.get
    val n = tn.get
    if ((n ne null) && taskTail.compareAndSet(tn, n)) {
      ts.add(n.task)
      drainRemainingTasks(ts)
    } else ts
  }
}

private class Worker(closing: AtomicInteger, taskTail: AtomicReference[TaskNode],
                     handler: Thread.UncaughtExceptionHandler, terminations: CountDownLatch) extends Runnable {
  def run() {
    try {
      doWork()
    } finally {
      terminations.countDown()
    }
  }

  private def doWork() {
    while (closing.get == 0) {
      try {
        val tn = taskTail.get
        val n = tn.get
        if (n eq null) backOff()
        else if (taskTail.compareAndSet(tn, n)) n.run()
      } catch {
        case ex: InterruptedException => return
        case ex: Throwable => onError(ex)
      }
    }
  }

  private def backOff() {
    LockSupport.parkNanos(100)
  }

  private def onError(ex: Throwable) {
    handler.uncaughtException(Thread.currentThread(), ex)
  }
}

private class TaskNode(var task: Runnable = null) extends AtomicReference[TaskNode] {
  def run() {
    task.run()
    task = null // to avoid holding of task reference when queue is empty
  }
}