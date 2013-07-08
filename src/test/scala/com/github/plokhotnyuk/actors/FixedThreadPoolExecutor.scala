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
                              name: String = "FixedThreadPool-" + FixedThreadPoolExecutor.poolId.getAndAdd(1)
                               ) extends AbstractExecutorService {
  private var head = new TaskNode()
  private var tail = head
  private val state = new AtomicInteger(0) // pool state (0 - running, 1 - shutdown, 2 - shutdownNow)
  private val terminations = new CountDownLatch(threadCount)
  private val threads = {
    val tf = threadFactory // to avoid creating of fields for a constructor params
    (1 to threadCount).map {
      i =>
        val wt = tf.newThread(new Runnable() {
          def run() {
            try {
              doWork()
            } catch {
              case ex: InterruptedException => // can occurs on shutdownNow when worker is backing off
            } finally {
              terminations.countDown()
            }
          }
        })
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
    val remainingTasks = new util.LinkedList[Runnable]()
    state.synchronized {
      var n = tail.next
      while (n ne null) {
        remainingTasks.add(n.task)
        n = n.next
      }
    }
    remainingTasks
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

  private def put(task: Runnable) {
    if (task == null) throw new NullPointerException()
    val n = new TaskNode(task)
    state.synchronized {
      val hn = head
      hn.next = n
      head = n
      if (tail eq hn) state.notify()
    }
  }

  @annotation.tailrec
  private def doWork() {
    if (state.get != 2) {
      val task = state.synchronized {
        val n = tail.next
        if (n eq null) {
          if (state.get == 0) {
            state.wait()
            null
          } else return
        } else {
          tail = n
          val task = n.task
          n.task = null
          task
        }
      }
      if (task ne null) run(task)
      doWork()
    }
  }

  private def run(task: Runnable) {
    try {
      task.run()
    } catch {
      case ex: InterruptedException => if (state.get != 2) onError(ex)
      case ex: Throwable => onError(ex)
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

private class TaskNode(var task: Runnable = null, var next: TaskNode = null)
