package com.github.plokhotnyuk.actors

import java.util.concurrent._
import java.util.concurrent.atomic._
import java.util.concurrent.locks.AbstractQueuedSynchronizer
import scala.annotation.tailrec

/**
 * A high performance implementation of thread pool with fixed number of threads.
 *
 * Implementation of task queue based on structure of non-intrusive MPSC node-based queue, described by Dmitriy Vyukov:
 * http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue
 *
 * An idea of using of semaphore to control of queue access borrowed from implementation of ThreadManager of JActor2:
 * https://github.com/laforge49/JActor2/blob/master/jactor-impl/src/main/java/org/agilewiki/jactor/impl/ThreadManagerImpl.java
 *
 * @param threadCount a number of worker threads in pool
 * @param threadFactory a factory to be used to build worker threads
 * @param handler the handler for internal worker threads that will be called
 *                in case of unrecoverable errors encountered while executing tasks.
 */
class FixedThreadPoolExecutor(threadCount: Int = Runtime.getRuntime.availableProcessors(),
                              threadFactory: ThreadFactory = new ThreadFactory() {
                                def newThread(r: Runnable): Thread = new Thread(r) {
                                  setDaemon(true)
                                }
                              },
                              handler: Thread.UncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
                                def uncaughtException(t: Thread, e: Throwable): Unit = e.printStackTrace()
                              }) extends AbstractExecutorService {
  private val head = new AtomicReference[TaskNode](new TaskNode())
  private val requests = new CountingSemaphore()
  private val running = new AtomicBoolean(true)
  private val tail = new AtomicReference[TaskNode](head.get)
  private val terminations = new CountDownLatch(threadCount)
  private val threads = {
    val tf = threadFactory // to avoid creating of field for the threadFactory constructor param
    (1 to threadCount).map(_ => tf.newThread(new Runnable() {
      def run(): Unit = doWork()
    }))
  }

  threads.foreach(_.start())

  def shutdown() {
    shutdownNow()
    awaitTermination(0, TimeUnit.MILLISECONDS)
  }

  def shutdownNow(): java.util.List[Runnable] = {
    running.lazySet(false)
    threads.filter(_ ne Thread.currentThread()).foreach(_.interrupt()) // don't interrupt worker thread due call in task
    drainTo(new java.util.LinkedList[Runnable](), tail.getAndSet(head.get)) // drain up to current head
  }

  def isShutdown: Boolean = !running.get

  def isTerminated: Boolean = terminations.getCount == 0

  def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = {
    if (threads.exists(_ eq Thread.currentThread())) terminations.countDown() // don't hang up due call in task
    terminations.await(timeout, unit)
  }

  def execute(task: Runnable) {
    enqueue(task)
    requests.releaseShared(1)
  }

  private def enqueue(task: Runnable) {
    if (task eq null) throw new NullPointerException
    val n = new TaskNode(task)
    head.getAndSet(n).lazySet(n)
  }

  @tailrec
  private def drainTo(ts: java.util.List[Runnable], tn: TaskNode): java.util.List[Runnable] =
    if (tn eq tail.get) ts
    else {
      val n = tn.get
      ts.add(n.task)
      drainTo(ts, n)
    }

  private def doWork() {
    try {
      while (running.get) {
        try {
          requests.acquireSharedInterruptibly(1)
          dequeueAndRun()
        } catch {
          case ex: Throwable => if (running.get) handler.uncaughtException(Thread.currentThread(), ex)
        }
      }
    } finally {
      terminations.countDown()
    }
  }

  @tailrec
  private def dequeueAndRun() {
    val tn = tail.get
    val n = tn.get
    if ((n ne null) && tail.compareAndSet(tn, n)) {
      val t = n.task
      n.task = null
      t.run()
    } else dequeueAndRun()
  }
}

private class CountingSemaphore extends AbstractQueuedSynchronizer() {
  private val count = new AtomicInteger()

  override protected final def tryReleaseShared(releases: Int): Boolean = {
    count.getAndAdd(releases)
    true
  }

  @tailrec
  override protected final def tryAcquireShared(acquires: Int): Int = {
    val available = count.get
    val remaining = available - acquires
    if (remaining < 0 || count.compareAndSet(available, remaining)) remaining
    else tryAcquireShared(acquires)
  }
}

private class TaskNode(var task: Runnable = null) extends AtomicReference[TaskNode]