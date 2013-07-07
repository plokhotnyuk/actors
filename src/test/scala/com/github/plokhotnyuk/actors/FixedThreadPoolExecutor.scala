package com.github.plokhotnyuk.actors

import java.util
import java.util.concurrent._
import java.util.concurrent.atomic.{AtomicReference, AtomicInteger}
import java.util.concurrent.locks.LockSupport

/**
 * An implementation of an `java.util.concurrent.ExecutorService ExecutorService`
 * with fixed number of pooled threads. It efficiently works at high rate of task submission and/or
 * when number of worker threads greater than available processors without overuse of CPU and
 * increasing latency between submission of tasks and starting of execution of them.
 *
 * For applications that require separate or custom pools, a `FixedThreadPoolExecutor`
 * may be constructed with a given pool size, that by default is equal to the number of available processors.
 *
 * All threads are created in constructor call using a `java.util.concurrent.ThreadFactory`.
 * If not otherwise specified, a default thread factory is used, that creates threads with daemon status.
 *
 * When running of tasks an uncaught exception can occurs. All unhandled exception are redirected to handler
 * that if not adjusted, by default, just print stack trace without stopping of execution of worker thread.
 *
 * Number of tasks which submitted but not yet executed is not limited, so
 * `java.util.concurrent.RejectedExecutionException` can occurs only after shutdown
 * when pool was initialized with default implementation of `onReject: Runnable => Unit`.
 *
 * @param threadCount   A number of worker threads in pool
 * @param threadFactory A factory to be used to build worker threads
 * @param onError       The exception handler for unhandled errors during executing of tasks
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
                              onReject: Runnable => Unit = t => throw new RejectedExecutionException(t.toString),
                              name: String = FixedThreadPoolExecutor.nextName()) extends AbstractExecutorService {
  private val head = new AtomicReference[TaskNode](new TaskNode())
  private val tail = new AtomicReference[TaskNode](head.get)
  private val state = new AtomicInteger(0) // pool state (0 - running, 1 - shutdown, 2 - shutdownNow)
  private val terminations = new CountDownLatch(threadCount)
  private val threads = {
    val (s, t, ts) = (state, tail, terminations) // to avoid long field names
    val (tf, oe) = (threadFactory, onError) // to avoid creating of fields for a constructor params
    (1 to threadCount).map {
      i =>
        val wt = tf.newThread(new Worker(s, t, oe, ts))
        wt.setName(name + "-worker-" + i)
        wt.start()
        wt
    }
  }

  def shutdown() {
    checkShutdownAccess()
    setState(1)
  }

  def shutdownNow(): util.List[Runnable] = {
    checkShutdownAccess()
    setState(2)
    threads.filter(_ ne Thread.currentThread()).foreach(_.interrupt()) // don't interrupt worker thread due call in task
    drainTo(new util.LinkedList[Runnable]())
  }

  def isShutdown: Boolean = state.get != 0

  def isTerminated: Boolean = terminations.getCount == 0

  def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = {
    if (threads.exists(_ eq Thread.currentThread())) terminations.countDown() // don't hang up due call in task
    terminations.await(timeout, unit)
  }

  def execute(task: Runnable) {
    if (state.get == 0) put(task)
    else onReject(task)
  }

  override def toString: String = name

  @annotation.tailrec
  private def drainTo(tasks: util.List[Runnable]): util.List[Runnable] = {
    val tn = tail.get
    val n = tn.get
    if (n eq null) tasks
    else if (tail.compareAndSet(tn, n)) {
      tasks.add(n.task)
      n.task = null
      drainTo(tasks)
    } else drainTo(tasks)
  }

  private def put(task: Runnable) {
    if (task == null) throw new NullPointerException()
    val n = new TaskNode(task)
    val hn = head.getAndSet(n)
    val wasEmpty = tail.get eq hn
    hn.lazySet(n)
    if (wasEmpty) signalNotEmpty()
  }

  private def signalNotEmpty() {
    state.synchronized {
      state.notify()
    }
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

private object FixedThreadPoolExecutor {
  private val poolId = new AtomicInteger(1)
  private val shutdownPerm = new RuntimePermission("modifyThread")

  def nextName(): String = "FixedThreadPool-" + poolId.getAndAdd(1)
}

private class Worker(state: AtomicInteger, tail: AtomicReference[TaskNode], onError: Throwable => Unit,
                     terminations: CountDownLatch) extends Runnable {
  def run() {
    try {
      doWork()
    } catch {
      case ex: InterruptedException => // can occurs on shutdownNow when worker is backing off
    } finally {
      terminations.countDown()
    }
  }

  @annotation.tailrec
  private def doWork() {
    if (state.get != 2) {
      val tn = tail.get
      val n = tn.get
      if (n eq null) {
          if (state.get != 0) return
          else waitUntilEmpty()
      } else if (tail.compareAndSet(tn, n)) {
        execute(n.task)
        n.task = null
      }
      doWork()
    }
  }

  private def execute(task: Runnable) {
    try {
      task.run()
    } catch {
      case ex: InterruptedException => if (state.get != 2) onError(ex)
      case ex: Throwable => onError(ex)
    }
  }

  private def waitUntilEmpty() {
    LockSupport.parkNanos(1)
    state.synchronized {
      while (tail.get.get eq null) {
        state.wait()
      }
    }
  }
}

private class TaskNode(var task: Runnable = null) extends AtomicReference[TaskNode]
