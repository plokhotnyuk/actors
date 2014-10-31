package scalaz.concurrent

import java.util.concurrent._
import scala.concurrent.forkjoin.{ForkJoinPool => ScalaForkJoinPool, ForkJoinTask => ScalaForkJoinTask, ForkJoinWorkerThread => ScalaForkJoinWorkerThread}

object ActorStrategy {
  def apply(exec: ExecutorService): Strategy = exec match {
    case fjp: ForkJoinPool =>
      new Strategy {
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
          val ct = Thread.currentThread
          if (ct.isInstanceOf[ForkJoinWorkerThread] && (ct.asInstanceOf[ForkJoinWorkerThread].getPool eq fjp)) t.fork()
          else fjp.execute(t)
          null
        }
      }
    case sfjp: ScalaForkJoinPool =>
      new Strategy {
        def apply[A](a: => A): () => A = {
          val t = new ScalaForkJoinTask[Unit] {
            override def getRawResult(): Unit = ()

            override def setRawResult(unit: Unit): Unit = ()

            override def exec(): Boolean = try {
              a
              true
            } catch {
              case ex: Throwable => onError(ex)
            }
          }
          val ct = Thread.currentThread
          if (ct.isInstanceOf[ScalaForkJoinWorkerThread] && (ct.asInstanceOf[ScalaForkJoinWorkerThread].getPool eq sfjp)) t.fork()
          else sfjp.execute(t)
          null
        }
      }
    case e =>
      new Strategy {
        def apply[A](a: => A): () => A = {
          e.execute(new Runnable() {
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
