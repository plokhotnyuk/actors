package scalaz.concurrent

import java.util.concurrent.ExecutorService

object ActorStrategy {
  def apply(exec: ExecutorService): Strategy = exec match {
    case fjp: scala.concurrent.forkjoin.ForkJoinPool =>
      import scala.concurrent.forkjoin._
      new Strategy {
        private val p = fjp

        def apply[A](a: => A): () => A = {
          val t = new ForkJoinTask[Unit] {
            override def getRawResult(): Unit = ()

            override def setRawResult(unit: Unit): Unit = ()

            override def exec(): Boolean = try {
              a
              true
            } catch {
              case ex: Throwable => onError(ex)
            }
          }
          Thread.currentThread match {
            case wt: ForkJoinWorkerThread if wt.getPool eq p => t.fork()
            case _ => p.execute(t)
          }
          null
        }
      }
    case fjp: java.util.concurrent.ForkJoinPool =>
      import java.util.concurrent._
      new Strategy {
        private val p = fjp

        def apply[A](a: => A): () => A = {
          val t = new ForkJoinTask[Unit] {
            override def getRawResult(): Unit = ()

            override def setRawResult(unit: Unit): Unit = ()

            override def exec(): Boolean = try {
              a
              true
            } catch {
              case ex: Throwable => onError(ex)
            }
          }
          Thread.currentThread match {
            case wt: ForkJoinWorkerThread if wt.getPool eq p => t.fork()
            case _ => p.execute(t)
          }
          null
        }
      }
    case e =>
      new Strategy {
        private val p = e

        def apply[A](a: => A): () => A = {
          p.execute(new Runnable() {
            def run(): Unit = try a catch {
              case ex: Throwable => onError(ex)
            }
          })
          null
        }
      }
  }

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
