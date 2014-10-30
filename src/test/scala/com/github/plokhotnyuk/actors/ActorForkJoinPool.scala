package com.github.plokhotnyuk.actors

import java.util.concurrent.{ForkJoinWorkerThread, ForkJoinTask, ForkJoinPool}

final class ActorForkJoinPool(parallelism: Int = Runtime.getRuntime.availableProcessors(),
                              factory: ForkJoinPool.ForkJoinWorkerThreadFactory = ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                              handler: Thread.UncaughtExceptionHandler = null)
  extends ForkJoinPool(parallelism, factory, handler, true) {
  override def execute(r: Runnable): Unit = {
    if (r eq null) throw new NullPointerException()
    val t = if (r.isInstanceOf[ForkJoinTask[_]]) r.asInstanceOf[ForkJoinTask[_]] else new ActorForkJoinTask(r)
    val ct = Thread.currentThread
    if (ct.isInstanceOf[ForkJoinWorkerThread] && (ct.asInstanceOf[ForkJoinWorkerThread].getPool eq this)) t.fork()
    else super.execute(t)
  }
}

@SerialVersionUID(1L)
private final class ActorForkJoinTask(r: Runnable) extends ForkJoinTask[Unit] {
  override def getRawResult(): Unit = {
    // do nothing
  }

  override def setRawResult(unit: Unit): Unit = {
    // do nothing
  }

  override def exec(): Boolean =
    try {
      r.run()
      true
    } catch {
      case ex: Throwable => ActorForkJoinTask.onError(ex)
    }
}

private object ActorForkJoinTask {
  private def onError(ex: Throwable): Boolean =
    if (ex.isInstanceOf[InterruptedException]) {
      Thread.currentThread.interrupt()
      false
    } else {
      val ct = Thread.currentThread
      val h = ct.getUncaughtExceptionHandler
      if (h ne null) h.uncaughtException(ct, ex)
      throw ex
    }
}
