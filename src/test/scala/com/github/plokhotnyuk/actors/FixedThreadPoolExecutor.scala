package com.github.plokhotnyuk.actors

import java.util.concurrent._
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong, AtomicReference}
import java.util.concurrent.locks.AbstractQueuedSynchronizer

/**
 * A high performance implementation of an `java.util.concurrent.ExecutorService ExecutorService`
 * with fixed number of pooled threads. It efficiently works with thousands of threads without overuse of CPU
 * and degradation of latency between submission of task and starting of it execution.
 *
 * For applications that require separate or custom pools, a `FixedThreadPoolExecutor`
 * may be constructed with a given pool size; by default, equal to the number of available processors.
 *
 * All threads are created in constructor call using a `java.util.concurrent.ThreadFactory`.
 * If not otherwise specified, a default thread factory is used, that creates threads with daemon status.
 *
 * When running of tasks an uncaught exception can occurs. All unhandled exception are redirected to handler
 * that if not adjusted, by default, just print stack trace without stopping of execution of worker thread.
 *
 * Number of submitted but not yet started tasks is practically unlimited.
 * `java.util.concurrent.RejectedExecutionException` can occurs only after shutdown
 * when pool was initialized with default implementation of `onReject: Runnable => Unit`.
 *
 * An implementation of task queue based on structure of
 * <a href="http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue">non-intrusive MPSC node-based queue</a>,
 * described by Dmitriy Vyukov.
 *
 * An idea of using of semaphore to control of queue access borrowed from
 * <a href="https://github.com/laforge49/JActor2/blob/master/jactor-impl/src/main/java/org/agilewiki/jactor/impl/ThreadManagerImpl.java">ThreadManager</a>,
 * implemented by Bill La Forge.
 *
 * Cooked at kitchen of <a href="https://github.com/plokhotnyuk/actors">actor benchmarks</a>.
 *
 * @param threadCount   A number of worker threads in pool
 * @param threadFactory A factory to be used to build worker threads
 * @param onError       The exception handler for unhandled errors during executing of tasks.
 * @param onReject      The handler for rejection of task submission after shutdown
 * @param name          A name of the executor service
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
                              },
                              name: String = {
                                "FixedThreadPool-" + FixedThreadPoolExecutor.poolId.getAndAdd(1)
                              }) extends AbstractExecutorService {
  private val head = new AtomicReference[TaskNode](new TaskNode())
  private val state = new AtomicInteger(0) // 0 - running, 1 - shutdown, 2 - shutdownNow
  private val requests = new CountingSemaphore()
  private val tail = new AtomicReference[TaskNode](head.get)
  private val terminations = new CountDownLatch(threadCount)
  private val threads = {
    val t = tail // to avoid long field names
    val s = state
    val ts = terminations
    val rs = requests
    val tf = threadFactory // to avoid creating of field for the threadFactory constructor param
    val oe = onError
    for (i <- 1 to threadCount) yield {
      val wt = tf.newThread(new Worker(s, rs, t, oe, ts))
      wt.setName(name + "-worker-" + i)
      wt
    }
  }

  threads.foreach(t => t.getState match {
    case Thread.State.NEW => t.start()
    case Thread.State.TERMINATED => throw new IllegalThreadStateException("Thread" + t + " is terminated.")
    case _ => // to be able to reuse already started threads
  })

  def shutdown() {
    checkShutdownAccess()
    setState(1)
  }

  def shutdownNow(): java.util.List[Runnable] = {
    checkShutdownAccess()
    setState(2)
    threads.filter(_ ne Thread.currentThread()).foreach(_.interrupt()) // don't interrupt worker thread due call in task
    drainTo(new java.util.LinkedList[Runnable]())
  }

  def isShutdown: Boolean = state.get != 0

  def isTerminated: Boolean = terminations.getCount == 0

  def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = {
    if (threads.exists(_ eq Thread.currentThread())) terminations.countDown() // don't hang up due call in task
    terminations.await(timeout, unit)
  }

  def execute(task: Runnable) {
    if (state.get == 0) {
      enqueue(task)
      requests.releaseShared(1)
    } else handleReject(task)
  }

  override def toString: String = name

  private def enqueue(task: Runnable) {
    if (task eq null) throw new NullPointerException
    val n = new TaskNode(task)
    head.getAndSet(n).lazySet(n)
  }

  @annotation.tailrec
  private def drainTo(ts: java.util.List[Runnable]): java.util.List[Runnable] = {
    val tn = tail.get
    val n = tn.get
    if (n eq null) ts
    else if (tail.compareAndSet(tn, n)) {
      ts.add(n.task)
      n.task = null
      drainTo(ts)
    } else drainTo(ts)
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

  @annotation.tailrec
  private def setState(newState: Int) {
    val currState = state.get
    if (newState > currState && !state.compareAndSet(currState, newState)) setState(newState)
  }
}

private class Worker(state: AtomicInteger, requests: CountingSemaphore, tail: AtomicReference[TaskNode],
                     onError: Throwable => Unit,  terminations: CountDownLatch) extends Runnable {
  def run() {
    try {
      doWork()
    } finally {
      terminations.countDown()
    }
  }

  @annotation.tailrec
  private def doWork() {
    val s = state.get
    if (s == 0 || (s == 1 && isNotEmpty)) {
      try {
        requests.acquireSharedInterruptibly(1)
        dequeueAndRun()
      } catch {
        case ex: Throwable => handleError(ex)
      }
      doWork()
    }
  }

  @annotation.tailrec
  private def dequeueAndRun() {
    val tn = tail.get
    val n = tn.get
    if ((n ne null) && tail.compareAndSet(tn, n)) {
      n.task.run()
      n.task = null
    } else dequeueAndRun()
  }

  private def isNotEmpty: Boolean = tail.get.get ne null

  private def handleError(ex: Throwable) {
    if (state.get != 2 || !ex.isInstanceOf[InterruptedException]) onError(ex)
  }
}

private object FixedThreadPoolExecutor {
  private val poolId = new AtomicInteger(1)
  private val shutdownPerm = new RuntimePermission("modifyThread")
}

private class CountingSemaphore extends AbstractQueuedSynchronizer() {
  private val count = new AtomicLong()

  def canBeAcquired: Boolean = count.get > 0

  override protected final def tryReleaseShared(releases: Int): Boolean = {
    count.getAndAdd(releases)
    true
  }

  @annotation.tailrec
  override protected final def tryAcquireShared(acquires: Int): Int = {
    val current = count.get
    val next = current - acquires
    if (next < 0 || count.compareAndSet(current, next)) next.toInt // don't worry, only sign of result value is used
    else tryAcquireShared(acquires)
  }
}

private class TaskNode(var task: Runnable = null) extends AtomicReference[TaskNode]
