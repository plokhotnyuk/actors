package scala.concurrent.forkjoin

import scalaz.concurrent.Strategy

class SFJPStrategy(p: ForkJoinPool) extends Strategy {
  def apply[A](a: => A): () => A = {
    val t = new ForkJoinTask[Unit] {
      def getRawResult(): Unit = ()

      def setRawResult(unit: Unit): Unit = ()

      def exec(): Boolean = {
        try a catch {
          case ex: Throwable => onError(ex)
        }
        false
      }
    }
    Thread.currentThread() match {
      case wt: ForkJoinWorkerThread if wt.getPool eq p => wt.workQueue.push(t)
      case _ => p.externalPush(t)
    }
    null
  }

  private def onError(ex: Throwable): Unit =
    if (ex.isInstanceOf[InterruptedException]) Thread.currentThread.interrupt()
    else {
      val ct = Thread.currentThread
      val h = ct.getUncaughtExceptionHandler
      if (h ne null) h.uncaughtException(ct, ex)
      throw ex
    }
}
