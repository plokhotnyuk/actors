package com.github.plokhotnyuk.actors

import java.util
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger

/**
 * A efficient implementation of an `java.util.concurrent.ExecutorService ExecutorService`
 * with fixed number of pooled threads.
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
 * Number of submitted but not yet started tasks depend on used `java.util.BlockingQueue BlockingQueue`
 * implementation and for default, when `ConcurrentLinkedBlockingQueue` is used, it is practically unlimited.
 * `java.util.concurrent.RejectedExecutionException` can occurs only after shutdown
 * when pool was initialized with default implementation of `onReject: Runnable => Unit`.
 *
 * Cooked at kitchen of <a href="https://github.com/plokhotnyuk/actors">actor benchmarks</a>.
 *
 * @param threadCount   A number of worker threads in pool
 * @param threadFactory A factory to be used to build worker threads
 * @param onError       The exception handler for unhandled errors during executing of tasks
 * @param onReject      The handler for rejection of task submission after shutdown
 * @param name          A name of the executor service
 * @param taskQueue     The queue to use for holding tasks before they are executed 
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
                                "FixedThreadPool-" + FixedThreadPoolExecutor.poolId.getAndIncrement()
                              },
                              taskQueue: BlockingQueue[Runnable] = {
                                FixedThreadPoolExecutor.newTaskQueue()
                              }) extends AbstractExecutorService {
  private val state = new AtomicInteger() // pool state (0 - running, 1 - shutdown, 2 - shutdownNow)
  private val terminations = new CountDownLatch(threadCount)
  private val threads = {
    val ts = terminations // to avoid long field name
    val tf = threadFactory // to avoid creating of field for a constructor param
    for (i <- 1 to threadCount) yield {
      val wt = tf.newThread(new Runnable() {
        def run() {
          try {
            doWork()
          } finally {
            ts.countDown()
          }
        }
      })
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
    val remainingTasks = new util.LinkedList[Runnable]()
    taskQueue.drainTo(remainingTasks)
    remainingTasks
  }

  def isShutdown: Boolean = state.get != 0

  def isTerminated: Boolean = terminations.getCount == 0

  def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = {
    if (threads.exists(_ eq Thread.currentThread())) terminations.countDown() // don't hang up due call in task
    terminations.await(timeout, unit)
  }

  def execute(task: Runnable) {
    if (state.get == 0) taskQueue.put(task)
    else onReject(task)
  }

  override def toString: String = name

  @annotation.tailrec
  private def doWork() {
    val s = state.get
    if (s == 0 || (s == 1 && !taskQueue.isEmpty)) {
      try {
        taskQueue.take().run()
      } catch {
        case ex: Throwable => handleError(ex)
      }
      doWork()
    }
  }

  private def handleError(ex: Throwable) {
    if (state.get != 2 || !ex.isInstanceOf[InterruptedException]) onError(ex)
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

  def newTaskQueue(): BlockingQueue[Runnable] = {
    System.getProperty("java.specification.version") match {
      case "1.8" => new ConcurrentLinkedBlockingQueue[Runnable](25, 8)
      case "1.7" => new ConcurrentLinkedBlockingQueue[Runnable](100, 16)
      case _ => new ConcurrentLinkedBlockingQueue[Runnable](400, 32)
    }
  }
}

