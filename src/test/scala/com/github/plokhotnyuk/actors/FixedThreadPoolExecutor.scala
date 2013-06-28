package com.github.plokhotnyuk.actors

import java.util.concurrent._
import java.util.concurrent.atomic.{AtomicInteger, AtomicBoolean, AtomicReference}
import java.util.concurrent.locks.LockSupport
import scala.annotation.tailrec

/**
 * A high performance implementation of an `java.util.concurrent.ExecutorService ExecutorService`
 * with fixed number of pooled threads. It efficiently works with hundreds of threads without overuse of CPU
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
 * Cooked at kitchen of <a href="https://github.com/plokhotnyuk/actors">actor benchmarks</a>.
 *
 * @param threadCount       A number of worker threads in pool
 * @param threadFactory     A factory to be used to build worker threads
 * @param onError           The exception handler for unhandled errors during executing of tasks.
 * @param onReject          The handler for rejection of task submission after shutdown
 * @param slowdownThreshold A number of tries before slowdown of worker thread when task queue is empty
 * @param parkThreshold     A number of tries before parking of worker thread when task queue is empty
 * @param name              A name of the executor service
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
                              slowdownThreshold: Int = 700,
                              parkThreshold: Int = 1000,
                              name: String = {
                                "FixedThreadPool-" + FixedThreadPoolExecutor.poolId.getAndAdd(1)
                              }) extends AbstractExecutorService {
  private val head = new AtomicReference[TaskNode](new TaskNode())
  private val running = new AtomicBoolean(true)
  private val waiters = new AtomicReference[List[Thread]](Nil)
  private val terminations = new CountDownLatch(threadCount)
  private val tail = new AtomicReference[TaskNode](head.get)
  private val threads = {
    val t = tail // to avoid long field names
    val r = running
    val ts = terminations
    val ws = waiters
    val tf = threadFactory // to avoid creating of field for the threadFactory constructor param
    val oe = onError
    val st = slowdownThreshold
    val pt = parkThreshold
    (1 to threadCount).map {
      i =>
        val wt = tf.newThread(new Worker(t, r, oe, st, pt, ts, ws))
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
      unpark()
    } else handleReject(task)
  }

  override def toString: String = name

  private def enqueue(task: Runnable) {
    if (task eq null) throw new NullPointerException
    val n = new TaskNode(task)
    head.getAndSet(n).lazySet(n)
  }

  @tailrec
  private def unpark() {
    val ws = waiters.get
    if (ws.isEmpty) ()
    else if (waiters.compareAndSet(ws, ws.tail)) LockSupport.unpark(ws.head)
    else unpark()
  }

  @annotation.tailrec
  private def drainTo(ts: java.util.List[Runnable], tn: TaskNode): java.util.List[Runnable] =
    if (tn eq tail.get) ts
    else {
      val n = tn.get
      ts.add(n.task)
      drainTo(ts, n)
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

private class Worker(tail: AtomicReference[TaskNode], running: AtomicBoolean,
                     onError: Throwable => Unit, slowdownThreshold: Int, parkThreshold: Int,
                     terminations: CountDownLatch, waiters: AtomicReference[List[Thread]]) extends Runnable {
  var backOffs = 0

  def run() {
    try {
      while (running.get || !isEmpty) {
        try {
          dequeueAndRun()
        } catch {
          case ex: Throwable => handleError(ex)
        }
      }
    } finally {
      terminations.countDown()
    }
  }

  @annotation.tailrec
  private def dequeueAndRun() {
    val tn = tail.get
    val n = tn.get
    if (n eq null) {
      backOff()
      dequeueAndRun()
    } else if (tail.compareAndSet(tn, n)) {
      n.task.run()
      n.task = null
      tuneBackOff()
    } else dequeueAndRun()
  }

  private def backOff() {
    backOffs += 1
    if (backOffs < slowdownThreshold) ()
    else if (backOffs < parkThreshold) LockSupport.parkNanos(1)
    else park(Thread.currentThread())
  }

  @tailrec
  private def park(w: Thread) {
    val ws = waiters.get
    if (waiters.compareAndSet(ws, w :: ws)) {
      LockSupport.park()
      if (Thread.interrupted()) w.interrupt()
      backOffs = 0
    } else park(w)
  }

  private def tuneBackOff() {
    backOffs = Math.max(slowdownThreshold - backOffs, 0)
  }

  private def isEmpty: Boolean = tail.get.get eq null

  private def handleError(ex: Throwable) {
    if (running.get || !ex.isInstanceOf[InterruptedException]) onError(ex)
  }
}

private object FixedThreadPoolExecutor {
  private val poolId = new AtomicInteger(1)
  private val shutdownPerm = new RuntimePermission("modifyThread")
}

private class TaskNode(var task: Runnable = null) extends AtomicReference[TaskNode]
