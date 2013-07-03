package com.github.plokhotnyuk.actors

import java.util.concurrent._
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import java.util.concurrent.locks.{LockSupport, ReentrantLock}
import java.util

/**
 * A high performance implementation of an `java.util.concurrent.ExecutorService ExecutorService`
 * with fixed number of pooled threads. It efficiently works at high rate of task submission and/or
 * when number of working threads greater than available processors. Its goal don't overuse of CPU and
 * don't increase latency between submission of tasks and starting of execution of them.
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
                              onReject: Runnable => Unit = t => throw new RejectedExecutionException("Task " + t + " rejected."),
                              name: String = "FixedThreadPool-" + FixedThreadPoolExecutor.poolId.getAndIncrement(),
                              taskQueue: BlockingQueue[Runnable] = new TaskQueue()) extends AbstractExecutorService {
  private val state = new AtomicInteger() // pool state (0 - running, 1 - shutdown, 2 - shutdownNow)
  private val terminations = new CountDownLatch(threadCount)
  private val threads = {
    val ts = terminations // to avoid long field names
    val tf = threadFactory // to avoid creating of field for the threadFactory constructor param
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
}

import TaskQueue._

class TaskQueue(totalSpins: Int = 300 / cpus,
                slowdownSpins: Int = 5 * cpus) extends util.AbstractQueue[Runnable] with BlockingQueue[Runnable] {
  private val head = new AtomicReference[TaskNode](new TaskNode())
  private val count = new AtomicInteger()
  private val notEmptyLock = new ReentrantLock()
  private val notEmptyCondition = notEmptyLock.newCondition()
  private val tail = new AtomicReference[TaskNode](head.get)

  final def size(): Int = count.get

  final def put(a: Runnable) {
    if (a == null) throw new NullPointerException()
    val n = new TaskNode(a)
    head.getAndSet(n).lazySet(n)
    if (count.getAndIncrement() == 0) signalNotEmpty()
  }

  final def take(): Runnable = take(totalSpins)

  final def drainTo(c: util.Collection[_ >: Runnable]): Int = drainTo(c, Integer.MAX_VALUE)

  @annotation.tailrec
  final def drainTo(c: util.Collection[_ >: Runnable], maxElements: Int): Int =
    if (maxElements <= 0) c.size()
    else {
      val tn = tail.get
      val n = tn.get
      if (n eq null) c.size()
      else if (tail.compareAndSet(tn, n)) {
        count.getAndDecrement()
        c.add(n.a)
        n.a = null
        drainTo(c, maxElements - 1)
      } else drainTo(c, maxElements)
    }

  def poll(): Runnable = ???

  def peek(): Runnable = ???

  def offer(e: Runnable): Boolean = ???

  def offer(e: Runnable, timeout: Long, unit: TimeUnit): Boolean = ???

  def poll(timeout: Long, unit: TimeUnit): Runnable = ???

  def remainingCapacity(): Int = ???

  def iterator(): util.Iterator[Runnable] = ???

  @annotation.tailrec
  private def take(i: Int): Runnable = {
    val tn = tail.get
    val n = tn.get
    if ((n ne null) && tail.compareAndSet(tn, n)) {
      count.getAndDecrement()
      val a = n.a
      n.a = null
      a
    } else take(backOff(i))
  }

  private def backOff(i: Int): Int =
    if (i > slowdownSpins) i - 1
    else if (i == slowdownSpins) {
      Thread.`yield`()
      i - 1
    } else if (i > 0) {
      LockSupport.parkNanos(1)
      i - 1
    } else {
      waitUntilEmpty()
      totalSpins
    }

  private def waitUntilEmpty() {
    notEmptyLock.lockInterruptibly()
    try {
      while (count.get == 0) {
        notEmptyCondition.await()
      }
    } finally {
      notEmptyLock.unlock()
    }
  }

  private def signalNotEmpty() {
    notEmptyLock.lock()
    try {
      notEmptyCondition.signal()
    } finally {
      notEmptyLock.unlock()
    }
  }
}

object TaskQueue {
  val cpus = Runtime.getRuntime.availableProcessors()
}

private class TaskNode(var a: Runnable = null) extends AtomicReference[TaskNode]
