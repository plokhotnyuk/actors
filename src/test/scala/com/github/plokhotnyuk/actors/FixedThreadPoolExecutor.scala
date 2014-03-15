package com.github.plokhotnyuk.actors

import java.util
import java.util.concurrent._
import java.util.concurrent.atomic.{AtomicReference, AtomicInteger}
import java.util.concurrent.locks.AbstractQueuedSynchronizer
import com.github.plokhotnyuk.actors.FixedThreadPoolExecutor._

/**
 * An implementation of an `java.util.concurrent.ExecutorService ExecutorService`
 * with fixed number of pooled threads. It efficiently works at high rate of task submission
 * and/or with thousands of worker threads without overuse of CPU and increasing latency
 * between submission of tasks and starting of execution of them.
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
 * Idea to use some implementation of 'java.util.concurrent.locks.AbstractQueuedSynchronizer' borrowed from
 * [[https://github.com/laforge49/JActor2/blob/master/jactor2-core/src/main/java/org/agilewiki/jactor2/core/facilities/ThreadManager.java]]
 *
 * @param poolSize       A number of worker threads in pool
 * @param threadFactory  A factory to be used to build worker threads
 * @param onError        The exception handler for unhandled errors during executing of tasks
 * @param onReject       The handler for rejection of task submission after shutdown
 * @param name           A name of the executor service
 * @param batch          A number of task completions before slowdown
 * @param spin           A number of checking if there is any task submitted before parking of worker thread
 */
class FixedThreadPoolExecutor(poolSize: Int = CPUs,
                              threadFactory: ThreadFactory = daemonThreadFactory(),
                              onError: Throwable => Unit = _.printStackTrace(),
                              onReject: Runnable => Unit = _ => throw new RejectedExecutionException,
                              name: String = generateName(),
                              batch: Int = 256 / CPUs,
                              spin: Int = 0) extends AbstractExecutorService {
  if (poolSize < 1) throw new IllegalArgumentException("poolSize should be greater than 0")
  private val head = new AtomicReference(new TaskNode)
  private val poller = new Poller(head.get, onError, batch, spin)
  private val terminations = new CountDownLatch(poolSize)
  private val threads = {
    val nm = name // to avoid long field name
    val tf = threadFactory // to avoid creation of field for constructor param
    (1 to poolSize).map {
      i =>
        val t = tf.newThread(new Runnable {
          def run(): Unit = work()
        })
        t.setName(s"$nm-worker-$i")
        t
    }.toArray
  }
  threads.foreach(_.start())

  def shutdown(): Unit = {
    checkShutdownAccess(threads)
    poller.updateState(1)
  }

  def shutdownNow(): util.List[Runnable] = {
    checkShutdownAccess(threads)
    poller.updateState(2)
    threads.filter(_ ne Thread.currentThread).foreach(_.interrupt()) // don't interrupt worker thread due call in task
    poller.drain()
  }

  def isShutdown: Boolean = poller.state != 0

  def isTerminated: Boolean = terminations.getCount == 0

  def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = {
    if (threads.exists(_ eq Thread.currentThread)) terminations.countDown() // don't hang up due call in task
    terminations.await(timeout, unit)
  }

  def execute(t: Runnable): Unit =
    if (t eq null) throw new NullPointerException
    else if (poller.state != 0) onReject(t)
    else {
      val n = new TaskNode(t)
      head.getAndSet(n).set(n)
      poller.releaseShared(1)
    }

  override def toString: String = s"${super.toString}[$status], pool size = ${threads.size}, name = $name]"

  private def work(): Unit =
    try work(poller) catch {
      case _: InterruptedException => // ignore due usage as control flow exception internally
    } finally terminations.countDown()

  @annotation.tailrec
  private def work(poller: Poller): Unit = {
    poller.acquireShared(1)
    work(poller)
  }

  private def status: String =
    if (isTerminated) "Terminated"
    else poller.state match {
      case 0 => "Running"
      case 1 => "Shutdown"
      case 2 => "Stop"
    }
}

private object FixedThreadPoolExecutor {
  private val CPUs = Runtime.getRuntime.availableProcessors
  private val poolId = new AtomicInteger
  private val shutdownPerm = new RuntimePermission("modifyThread")

  def checkShutdownAccess(ts: Seq[Thread]): Unit =
    Option(System.getSecurityManager).foreach {
      sm =>
        sm.checkPermission(shutdownPerm)
        ts.foreach(sm.checkAccess)
    }

  def daemonThreadFactory(): ThreadFactory = new ThreadFactory {
    def newThread(worker: Runnable): Thread = new Thread(worker) {
      setDaemon(true)
    }
  }

  def generateName(): String = s"FixedThreadPool-${poolId.incrementAndGet()}"
}

private final class Poller(tn: TaskNode, onError: Throwable => Unit,
                           batch: Int, spin: Int) extends AbstractQueuedSynchronizer {
  private val tail = new AtomicReference(tn)

  def state = getState

  @annotation.tailrec
  def updateState(newState: Int): Unit = {
    val currState = getState
    if (newState > currState && !compareAndSetState(currState, newState)) updateState(newState)
  }

  @annotation.tailrec
  def drain(ts: util.List[Runnable] = new util.LinkedList[Runnable](), tn: TaskNode = tail.get): util.List[Runnable] = {
    val n = tn.get
    if (n eq null) {
      tn.task = null
      tail.set(tn)
      ts
    } else {
      ts.add(n.task)
      drain(ts, n)
    }
  }

  override protected def tryReleaseShared(ignore: Int): Boolean = true

  override protected def tryAcquireShared(ignore: Int): Int = pollAndRun(batch, spin)

  @annotation.tailrec
  private def pollAndRun(i: Int, j: Int): Int = {
    val tn = tail.get
    val n = tn.get
    if (n ne null) {
      if (tail.compareAndSet(tn, n)) {
        try n.task.run() catch {
          case ex: Throwable => onError(ex)
        } finally n.task = null // to avoid possible memory leak when queue is empty
        if (getState > 1) throw new InterruptedException
        else if (i != 0) pollAndRun(i - 1, spin)
        else 0 // slowdown to avoid starvation
      } else pollAndRun(batch, spin)
    } else if (getState > 0) throw new InterruptedException
    else if (j != 0) pollAndRun(batch, j - 1)
    else -1
  }
}

private class TaskNode(var task: Runnable = null) extends AtomicReference[TaskNode]
