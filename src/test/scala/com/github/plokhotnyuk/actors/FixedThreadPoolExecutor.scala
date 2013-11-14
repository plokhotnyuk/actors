package com.github.plokhotnyuk.actors

import java.util
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger

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
 * @param poolSize       A number of worker threads in pool
 * @param threadFactory  A factory to be used to build worker threads
 * @param onError        The exception handler for unhandled errors during executing of tasks
 * @param onReject       The handler for rejection of task submission after shutdown
 * @param name           A name of the executor service
 */
class FixedThreadPoolExecutor(poolSize: Int = Runtime.getRuntime.availableProcessors(),
                              threadFactory: ThreadFactory = FixedThreadPoolExecutor.daemonThreadFactory(),
                              onError: Throwable => Unit = _.printStackTrace(),
                              onReject: Runnable => Unit = t => throw new RejectedExecutionException(t.toString),
                              name: String = FixedThreadPoolExecutor.generateName()) extends AbstractExecutorService {
  private val tasks = new ConcurrentLinkedQueue[Runnable]()
  private val taskCount = new Semaphore(0)
  private val state = new AtomicInteger() // pool state (0 - running, 1 - shutdown, 2 - stop)
  private val terminations = new CountDownLatch(poolSize)
  private val threads = {
    val tf = threadFactory // to avoid creating of fields for a constructor params
    val ts = terminations // to avoid long field name
    (1 to poolSize).map {
      i =>
        val wt = tf.newThread(new Runnable() {
          def run(): Unit =
            try doWork(state.get) catch {
              case ex: InterruptedException => // can occurs on shutdownNow when worker is backing off
            } finally ts.countDown()
        })
        wt.setName(name + "-worker-" + i)
        wt.start()
        wt
    }
  }

  def shutdown(): Unit = {
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
    taskCount.release(threads.size)
    if (threads.exists(_ eq Thread.currentThread())) terminations.countDown() // don't hang up due call in task
    terminations.await(timeout, unit)
  }

  def execute(t: Runnable): Unit =
    if (t eq null) throw new NullPointerException()
    else if (state.get != 0) onReject(t)
    else {
      tasks.offer(t)
      taskCount.release()
    }

  override def toString: String =
    super.toString + "[" + status + ", pool size = " + threads.size + ", name = " + name + "]"

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
  private def doWork(s: Int): Unit = {
    taskCount.acquire()
    run(tasks.poll())
    if (s != 2) doWork(state.get)
  }

  private def run(t: Runnable): Unit =
    if (t ne null) try t.run() catch {
      case ex: Throwable => if (!ex.isInstanceOf[InterruptedException] || state.get != 2) onError(ex)
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

  private def checkShutdownAccess(): Unit =
    Option(System.getSecurityManager).foreach {
      sm =>
        sm.checkPermission(FixedThreadPoolExecutor.shutdownPerm)
        threads.foreach(sm.checkAccess)
    }
}

private object FixedThreadPoolExecutor {
  private val poolId = new AtomicInteger()
  private val shutdownPerm = new RuntimePermission("modifyThread")
  
  def daemonThreadFactory(): ThreadFactory = new ThreadFactory() {
    def newThread(worker: Runnable): Thread = new Thread(worker) {
      setDaemon(true)
    }
  }
  
  def generateName(): String = "FixedThreadPool-" + poolId.incrementAndGet()
}
