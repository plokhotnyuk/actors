package scalaz.concurrent

import java.util.concurrent.ExecutorService

object ActorStrategy {
  def apply(exec: ExecutorService): Strategy = exec match {
    case p: scala.concurrent.forkjoin.ForkJoinPool => new Strategy {

      import scala.concurrent.forkjoin.ForkJoinTask

      def apply[A](a: => A): () => A = {
        val t = new ForkJoinTask[Unit] {
          def getRawResult: Unit = ()

          def setRawResult(unit: Unit): Unit = ()

          def exec(): Boolean = {
            try a catch {
              case ex: Throwable => onError(ex)
            }
            false
          }
        }
        if (ForkJoinTask.getPool eq p) t.fork()
        else p.execute(t)
        null
      }
    }
    case p: java.util.concurrent.ForkJoinPool => new Strategy {

      import java.util.concurrent.ForkJoinTask

      def apply[A](a: => A): () => A = {
        val t = new ForkJoinTask[Unit] {
          def getRawResult: Unit = ()

          def setRawResult(unit: Unit): Unit = ()

          def exec(): Boolean = {
            try a catch {
              case ex: Throwable => onError(ex)
            }
            false
          }
        }
        if (ForkJoinTask.getPool eq p) t.fork()
        else p.execute(t)
        null
      }
    }
    case p => new Strategy {
      def apply[A](a: => A): () => A = {
        p.execute(new Runnable {
          def run(): Unit = a
        })
        null
      }
    }
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
