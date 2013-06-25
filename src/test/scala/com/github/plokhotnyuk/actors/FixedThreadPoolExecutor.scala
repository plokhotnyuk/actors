package com.github.plokhotnyuk.actors

import java.util.concurrent._
import java.util.concurrent.atomic._
import java.util.concurrent.locks.AbstractQueuedSynchronizer
import scala.annotation.tailrec

/**
 * A high performance implementation of an {@link java.util.concurrent.ExecutorService ExecutorService}
 * with fixed number of pooled threads. It efficiently works with thousands of threads without overuse of CPU
 * and degradation of latency between task submit and starting of its execution.
 *
 * <p>For applications that require separate or custom pools, a {@code FixedThreadPoolExecutor}
 * may be constructed with a given pool size; by default, equal to the number of available processors.
 *
 * <p>All threads are created in constructor call using a {@link java.util.concurrent.ThreadFactory ThreadFactory}.
 * If not otherwise specified, a default thread factory is used, that creates threads with daemon status.
 *
 * <p>When running of tasks an uncaught exception can occurs. All unhandled exception are redirected to
 * provided handler that (by default) just print stack trace without stopping of worker thread execution.
 *
 * <p>Number of submitted but not yet started tasks practically is unlimited.
 * {@link java.util.concurrent.RejectedExecutionException RejectedExecutionException} can occurs
 * only after shutdown when pool was initialized with default implementation of onReject.
 *
 * <p>An implementation of task queue based on structure of
 * <a href="http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue">non-intrusive MPSC node-based queue</a>,
 * described by Dmitriy Vyukov.
 *
 * <p>An idea of using of semaphore to control of queue access borrowed from
 * <a href="https://github.com/laforge49/JActor2/blob/master/jactor-impl/src/main/java/org/agilewiki/jactor/impl/ThreadManagerImpl.java">ThreadManager</a>,
 * implemented by Bill La Forge.
 *
 * <p>Cooked at <a href="https://github.com/plokhotnyuk/actors">actor benchmark kitchen</a>.
 *
 * @param threadCount a number of worker threads in pool
 * @param threadFactory a factory to be used to build worker threads
 * @param onError the handler for internal worker threads that will be called
 *                in case of unrecoverable errors encountered while executing tasks.
 * @param onReject the handler for rejection of task submission after shutdown
 */
class FixedThreadPoolExecutor(threadCount: Int = Runtime.getRuntime.availableProcessors(),
                              threadFactory: ThreadFactory = new ThreadFactory() {
                                def newThread(worker: Runnable): Thread = new Thread(worker) {
                                  setDaemon(true)
                                }
                              },
                              onError: Throwable => Unit = _.printStackTrace(),
                              onReject: Runnable => Unit = {
                                t => throw new RejectedExecutionException("Task " + t + " rejected.")
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

  threads.foreach(t => t.getState match {
    case Thread.State.NEW => t.start()
    case Thread.State.TERMINATED => throw new IllegalThreadStateException("Thread" + t + " is terminated.")
    case _ => // do nothing, but warning would be helpful
  })

  def shutdown() {
    checkShutdownAccess()
    running.lazySet(false)
  }

  def shutdownNow(): java.util.List[Runnable] = {
    shutdown()
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
    if (running.get) {
      enqueue(task)
      requests.releaseShared(1)
    } else handleReject(task)
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
          case ex: Throwable => handleError(ex)
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

  private def handleError(ex: Throwable) {
    if (running.get || !ex.isInstanceOf[InterruptedException]) onError(ex)
  }

  private def handleReject(task: Runnable) {
    onReject(task)
  }

  private def checkShutdownAccess() {
    val security = System.getSecurityManager
    if (security != null) {
      security.checkPermission(FixedThreadPoolExecutor.shutdownPerm)
      threads.foreach(security.checkAccess(_))
    }
  }
}

private object FixedThreadPoolExecutor {
  private val shutdownPerm = new RuntimePermission("modifyThread")
}

private class CountingSemaphore extends AbstractQueuedSynchronizer() {
  private val count = new AtomicLong()

  override protected final def tryReleaseShared(releases: Int): Boolean = {
    count.getAndAdd(releases)
    true
  }

  @tailrec
  override protected final def tryAcquireShared(acquires: Int): Int = {
    val current = count.get
    val next = current - acquires
    if (next < 0 || count.compareAndSet(current, next)) next.toInt // only sign of value used
    else tryAcquireShared(acquires)
  }
}

private class TaskNode(var task: Runnable = null) extends AtomicReference[TaskNode]
