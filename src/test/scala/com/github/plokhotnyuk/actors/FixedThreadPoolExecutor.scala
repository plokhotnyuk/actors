package com.github.plokhotnyuk.actors

import java.util
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger
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
 * @param poolSize       A number of worker threads in pool
 * @param threadFactory  A factory to be used to build worker threads
 * @param onError        The exception handler for unhandled errors during executing of tasks
 * @param onReject       The handler for rejection of task submission after shutdown
 * @param name           A name of the executor service
 */
class FixedThreadPoolExecutor(poolSize: Int = CPUs,
                              threadFactory: ThreadFactory = daemonThreadFactory(),
                              onError: Throwable => Unit = _.printStackTrace(),
                              onReject: Runnable => Unit = _ => throw new RejectedExecutionException,
                              name: String = generateName()) extends AbstractExecutorService {
  if (poolSize < 1) throw new IllegalArgumentException("poolSize should be greater than 0")
  private val state = new AtomicInteger
  private val queue = new LinkedBlockingQueue[Runnable]
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
    updateState(1)
  }

  def shutdownNow(): util.List[Runnable] = {
    checkShutdownAccess(threads)
    updateState(2)
    threads.filter(_ ne Thread.currentThread).foreach(_.interrupt()) // don't interrupt worker thread due call in task
    new util.LinkedList[Runnable](queue)
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
    else queue.offer(t)

  override def toString: String = s"${super.toString}[$status], pool size = ${threads.size}, name = $name]"

  @annotation.tailrec
  private def updateState(ns: Int): Unit = {
    val s = state.get
    if (ns > s && !state.compareAndSet(s, ns)) updateState(ns)
  }

  private def work(): Unit =
    try {
      val q = queue
      val s = state
      while (s.get <= 1) {
        val t = q.take()
        try t.run() catch {
          case ex: Throwable => onError(ex)
        }
      }
    } catch {
      case _: InterruptedException => // ignore due usage as control flow exception internally
    } finally terminations.countDown()

  private def status: String =
    if (isTerminated) "Terminated"
    else state.get match {
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
