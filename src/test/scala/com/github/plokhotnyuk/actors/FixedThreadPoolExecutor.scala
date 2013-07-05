package com.github.plokhotnyuk.actors

import java.util
import java.util.concurrent._
import java.util.concurrent.atomic.{AtomicReference, AtomicInteger}
import java.util.concurrent.locks.{LockSupport, Condition, ReentrantLock}

/**
 * A efficient implementation of an `java.util.concurrent.ExecutorService ExecutorService`
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
 * Cooked at kitchen of <a href="https://github.com/plokhotnyuk/actors">actor benchmarks</a>.
 *
 * @param threadCount       A number of worker threads in pool
 * @param threadFactory     A factory to be used to build worker threads
 * @param onError           The exception handler for unhandled errors during executing of tasks
 * @param onReject          The handler for rejection of task submission after shutdown
 * @param name              A name of the executor service
 * @param slowdownThreshold A number of spins before starting of slowdown of worker thread
 * @param parkThreshold     A number of spins before parking of worker thread
 */
class FixedThreadPoolExecutor(threadCount: Int = Runtime.getRuntime.availableProcessors(),
                              threadFactory: ThreadFactory = new ThreadFactory() {
                                def newThread(worker: Runnable): Thread = new Thread(worker) {
                                  setDaemon(true)
                                }
                              },
                              onError: Throwable => Unit = _.printStackTrace(),
                              onReject: Runnable => Unit = t => throw new RejectedExecutionException(t.toString),
                              name: String = "FixedThreadPool-" + FixedThreadPoolExecutor.poolId.getAndAdd(1),
                              slowdownThreshold: Int = 1000, parkThreshold: Int = 2000) extends AbstractExecutorService {
  private val head = new AtomicReference[TaskNode](new TaskNode())
  private val state = new AtomicInteger() // pool state (0 - running, 1 - shutdown, 2 - shutdownNow)
  private val notEmptyLock = new ReentrantLock()
  private val notEmptyCondition = notEmptyLock.newCondition()
  private val terminations = new CountDownLatch(threadCount)
  private val tail = new AtomicReference[TaskNode](head.get)
  private val threads = {
    val s = state // to avoid long field name
    val t = tail
    val nel = notEmptyLock
    val nec = notEmptyCondition
    val ts = terminations
    val tf = threadFactory // to avoid creating of field for a constructor param
    val oe = onError
    val st = slowdownThreshold
    val pt = parkThreshold
    for (i <- 1 to threadCount) yield {
      val wt = tf.newThread(new Worker(s, t, oe, nel, nec, ts, st, pt))
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
    val wasEmpty = hn eq tail.get
    hn.lazySet(n)
    if (wasEmpty) signalNotEmpty()
  }

  private def signalNotEmpty() {
    notEmptyLock.lock()
    try {
      notEmptyCondition.signal()
    } finally {
      notEmptyLock.unlock()
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
}

private class Worker(state: AtomicInteger, tail: AtomicReference[TaskNode], onError: Throwable => Unit,
                     notEmptyLock: ReentrantLock, notEmptyCondition: Condition, terminations: CountDownLatch,
                     slowdownThreshold: Int, parkThreshold: Int) extends Runnable {
  private var optimalSpins = slowdownThreshold // don't overuse CPU on start
  private var spins = optimalSpins

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
      if ((n ne null) && tail.compareAndSet(tn, n)) {
        execute(n.task)
        n.task = null
        tuneSpins()
      } else if (state.get != 0) return
      else backOff()
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

  private def tuneSpins() {
    optimalSpins = (slowdownThreshold - spins  + optimalSpins) >> 1
    spins = optimalSpins
  }

  private def backOff() {
    spins += 1
    if (spins <= slowdownThreshold) ()
    else if (spins <= parkThreshold) LockSupport.parkNanos(1)
    else {
      waitUntilEmpty()
      spins = slowdownThreshold
    }
  }

  private def waitUntilEmpty() {
    notEmptyLock.lockInterruptibly()
    try {
      while (tail.get.get eq null) {
        notEmptyCondition.await()
      }
    } finally {
      notEmptyLock.unlock()
    }
  }
}

private class TaskNode(var task: Runnable = null) extends AtomicReference[TaskNode]
