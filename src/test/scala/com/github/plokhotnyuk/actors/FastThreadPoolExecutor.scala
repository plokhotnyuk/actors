package com.github.plokhotnyuk.actors

import java.util.concurrent._
import java.util
import java.util.concurrent.atomic.AtomicInteger
import java.lang.InterruptedException
import scala.annotation.tailrec

class FastThreadPoolExecutor(parallelism: Int, threadFactory: ThreadFactory) extends AbstractExecutorService {
  private val tasks = new ConcurrentLinkedQueue[Runnable]()
  private val taskRequests = new Semaphore(0)
  private val toClose = new AtomicInteger(0)
  private val threads = (1 to parallelism).map(workerThread(_))

  def shutdown() {
    shutdownNow()
    awaitTermination(0, TimeUnit.MILLISECONDS)
  }

  def shutdownNow(): util.List[Runnable] = {
    toClose.set(0)
    taskRequests.release(parallelism)
    threads.foreach(_.interrupt())
    new util.ArrayList(tasks)
  }

  def isShutdown: Boolean = toClose.intValue() == 0

  def isTerminated: Boolean = threads.forall(_.isAlive)

  def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = {
    val terminated = new AtomicInteger(parallelism)
    val terminator = new Thread() {
      override def run() {
        threads.foreach {
          t =>
            doIgnoringInterrupt(t.join())
            terminated.decrementAndGet()
        }
      }
    }
    terminator.start()
    doIgnoringInterrupt(terminator.join(unit.toMillis(timeout)))
    terminated.intValue() == 0
  }

  def execute(command: Runnable) {
    tasks.offer(command)
    taskRequests.release()
  }

  private def workerThread(i: Int): Thread = {
    val thread = threadFactory.newThread(new Runnable() {
      def run() {
        doIgnoringInterrupt(doWork())
      }
    })
    thread.start()
    thread
  }

  @tailrec
  private def doWork() {
    if (toClose.intValue() == 0) {
      taskRequests.acquire()
      val task = tasks.poll()
      if (task ne null) task.run()
      doWork()
    }
  }

  private def doIgnoringInterrupt(code: => Unit) {
    try {
      code
    } catch {
      case ex: InterruptedException => // ignore
    }
  }
}
