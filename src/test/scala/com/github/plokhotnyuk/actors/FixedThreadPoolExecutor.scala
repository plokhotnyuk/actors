package com.github.plokhotnyuk.actors

import java.util
import java.util.concurrent._
import java.util.concurrent.atomic.{AtomicReference, AtomicInteger}
import java.util.concurrent.locks.AbstractQueuedSynchronizer

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
 * An implementation of task queue based on MultiLane over MPMC queues described by Dmitriy Vyukov:
 * [[http://www.1024cores.net/home/scalable-architecture/multilane]]
 *
 * Idea to use some implementation of 'java.util.concurrent.locks.AbstractQueuedSynchronizer' borrowed from
 * [[https://github.com/laforge49/JActor2/blob/master/jactor2-core/src/main/java/org/agilewiki/jactor2/core/facilities/ThreadManager.java]]
 *
 * @param poolSize       A number of worker threads in pool
 * @param threadFactory  A factory to be used to build worker threads
 * @param onError        The exception handler for unhandled errors during executing of tasks
 * @param onReject       The handler for rejection of task submission after shutdown
 * @param name           A name of the executor service
 * @param spin           A number of tries before slowdown of worker thread
 */
class FixedThreadPoolExecutor(poolSize: Int = FixedThreadPoolExecutor.CPUs,
                              threadFactory: ThreadFactory = FixedThreadPoolExecutor.daemonThreadFactory(),
                              onError: Throwable => Unit = _.printStackTrace(),
                              onReject: Runnable => Unit = t => throw new RejectedExecutionException(t.toString),
                              name: String = FixedThreadPoolExecutor.generateName(),
                              spin: Int = FixedThreadPoolExecutor.optimalSpin) extends AbstractExecutorService {
  private val tasks = new MultiLaneQueue(poolSize)
  private val state = new AtomicInteger // pool state (0 - running, 1 - shutdown, 2 - stop)
  private val sync = new AbstractQueuedSynchronizer {
    override protected final def tryReleaseShared(ignore: Int): Boolean = true

    override protected final def tryAcquireShared(ignore: Int): Int = work()
  }
  private val terminations = new CountDownLatch(poolSize)
  private val threads = createThreads(threadFactory)

  threads.foreach(_.start())

  def shutdown(): Unit = {
    checkShutdownAccess()
    setState(1)
  }

  def shutdownNow(): util.List[Runnable] = {
    checkShutdownAccess()
    setState(2)
    threads.filter(_ ne Thread.currentThread).foreach(_.interrupt()) // don't interrupt worker thread due call in task
    drainTo(new util.LinkedList[Runnable])
  }

  def isShutdown: Boolean = state.get != 0

  def isTerminated: Boolean = terminations.getCount == 0

  def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = {
    if (threads.exists(_ eq Thread.currentThread)) terminations.countDown() // don't hang up due call in task
    terminations.await(timeout, unit)
  }

  def execute(t: Runnable): Unit =
    if (t eq null) throw new NullPointerException
    else if (state.get != 0) onReject(t)
    else {
      tasks.offer(t)
      sync.releaseShared(1)
    }

  override def toString: String = s"${super.toString}[$status], pool size = ${threads.size}, name = $name]"

  @annotation.tailrec
  private def createThreads(tf: ThreadFactory, maskedIds: Set[Int] = Set(), acc: List[Thread] = Nil): List[Thread] = {
    val ts = terminations // to avoid long field name
    val t = tf.newThread(new Runnable {
      def run(): Unit =
        try loop() catch {
          case _: InterruptedException => // ignore
        } finally ts.countDown()
    })
    val maskedId = t.getId.toInt & tasks.mask
    if (maskedIds.contains(maskedId)) {
      if (maskedIds.size > tasks.mask) acc
      else createThreads(tf, maskedIds, acc)
    } else {
      t.setName(s"$name-worker-${maskedIds.size}")
      createThreads(tf, maskedIds + maskedId, t :: acc)
    }
  }

  @annotation.tailrec
  private def drainTo(ts: util.List[Runnable]): util.List[Runnable] = {
    val t = tasks.poll()
    if (t eq null) ts
    else {
      ts.add(t)
      drainTo(ts)
    }
  }

  @annotation.tailrec
  private def loop(): Unit = {
    try sync.acquireSharedInterruptibly(1) catch {
      case _: InterruptedException => return
      case ex: Throwable => onError(ex)
    }
    loop()
  }

  @annotation.tailrec
  private def work(s: Int = spin): Int = {
    val t = tasks.poll()
    if (t ne null) {
      t.run()
      if (state.get == 2) throw new InterruptedException
      if (s > 0) work(s - 1) else 1 // slowdown to allow other worker to catch something
    } else {
      if (state.get != 0) throw new InterruptedException
      if (s > 0) work(s - 1) else -1
    }
  }

  @annotation.tailrec
  private def setState(newState: Int): Unit = {
    val currState = state.get
    if (newState > currState && !state.compareAndSet(currState, newState)) setState(newState)
  }

  private def status: String =
    if (isTerminated) "Terminated"
    else state.get match {
      case 0 => "Running"
      case 1 => "Shutdown"
      case 2 => "Stop"
    }

  private def checkShutdownAccess(): Unit = {
    val ts = threads // to avoid long field name
    Option(System.getSecurityManager).foreach {
      sm =>
        sm.checkPermission(FixedThreadPoolExecutor.shutdownPerm)
        ts.foreach(sm.checkAccess)
    }
  }
}

private object FixedThreadPoolExecutor {
  private val CPUs = Runtime.getRuntime.availableProcessors
  private val poolId = new AtomicInteger
  private val shutdownPerm = new RuntimePermission("modifyThread")

  def daemonThreadFactory(): ThreadFactory = new ThreadFactory {
    def newThread(worker: Runnable): Thread = new Thread(worker) {
      setDaemon(true)
    }
  }

  def generateName(): String = s"FixedThreadPool-${poolId.incrementAndGet()}"

  def optimalSpin: Int = 256 / CPUs
}

private class MultiLaneQueue(initialCapacity: Int) {
  val capacity = optimalCapacity(initialCapacity)
  val mask = capacity - 1
  private val tails = (1 to capacity).map(_ => new PaddedAtomicReference(new TaskNode(null))).toArray
  private val heads = tails.map(n => new PaddedAtomicReference(n.get)).toArray

  def offer(t: Runnable): Unit = {
    val n = new TaskNode(t)
    heads(mask & currentThreadId).getAndSet(n).set(n)
  }

  def poll(): Runnable = {
    val offset = currentThreadId
    poll(offset, offset + capacity)
  }

  @annotation.tailrec
  final def poll(offset: Int, limit: Int): Runnable = {
    val tail = tails(mask & offset)
    val tn = tail.get
    val n = tn.get
    if (n eq null) {
      if (offset < limit) poll(offset + 1, limit)
      else null
    } else if (tail.compareAndSet(tn, n)) {
      val t = n.task
      n.task = null
      t
    } else poll(offset, limit)
  }

  private def currentThreadId: Int = Thread.currentThread().getId.toInt

  private def optimalCapacity(initialCapacity: Int): Int = {
    val parallelism = Math.min(initialCapacity, Runtime.getRuntime.availableProcessors)
    if (isPowerOfTwo(parallelism)) parallelism
    else nextPowerOfTwo(parallelism) >> 1
  }

  private def nextPowerOfTwo(x: Int): Int = 1 << (32 - Integer.numberOfLeadingZeros(x - 1))

  private def isPowerOfTwo(x: Int): Boolean = (x & (x - 1)) == 0
}

private class TaskNode(var task: Runnable) extends AtomicReference[TaskNode]

private class PaddedAtomicReference[T](t: T) extends AtomicReference[T](t) {
  @volatile var p1, p2, p3, p4, p5, p6: Long = _

  def sumPaddingToPreventOptimization: Long = p1 + p2 + p3 + p4 + p5 + p6
}