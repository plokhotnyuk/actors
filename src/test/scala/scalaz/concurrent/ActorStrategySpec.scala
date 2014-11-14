package scalaz.concurrent

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent._
import org.specs2.mutable.Specification
import scalaz.concurrent.Actor2._

class ActorStrategySpec extends Specification {
  val NumOfThreads = 4

  "actor strategy" should {
    "execute code async for different pools" in {
      val l = new CountDownLatch(3)
      unboundedActor((_: Int) => l.countDown()) {
        import scala.concurrent.forkjoin._
        ActorStrategy(new ForkJoinPool(NumOfThreads))
      } ! 1
      unboundedActor((_: Int) => l.countDown()) {
        ActorStrategy(new ForkJoinPool(NumOfThreads))
      } ! 1
      unboundedActor((_: Int) => l.countDown()) {
        ActorStrategy(Executors.newFixedThreadPool(NumOfThreads))
      } ! 1
      assertCountDown(l)
    }

    "redirect unhandled errors to thread's uncaught exception handler of different pools" in {
      val l = new CountDownLatch(3)
      val h = new UncaughtExceptionHandler() {
        override def uncaughtException(t: Thread, e: Throwable): Unit = l.countDown()
      }
      unboundedActor((_: Int) => throw new RuntimeException()) {
        import scala.concurrent.forkjoin._
        ActorStrategy(new ForkJoinPool(NumOfThreads, new ForkJoinPool.ForkJoinWorkerThreadFactory() {
          override def newThread(pool: ForkJoinPool): ForkJoinWorkerThread = new ForkJoinWorkerThread(pool) {
            setUncaughtExceptionHandler(h)
          }
        }, null, true))
      } ! 1
      unboundedActor((_: Int) => throw new RuntimeException()) {
        ActorStrategy(new ForkJoinPool(NumOfThreads, new ForkJoinPool.ForkJoinWorkerThreadFactory() {
          override def newThread(pool: ForkJoinPool): ForkJoinWorkerThread = new ForkJoinWorkerThread(pool) {
            setUncaughtExceptionHandler(h)
          }
        }, null, true))
      } ! 1
      unboundedActor((_: Int) => throw new RuntimeException()) {
        ActorStrategy(new ThreadPoolExecutor(NumOfThreads, NumOfThreads, 0L, TimeUnit.MILLISECONDS,
          new LinkedBlockingQueue[Runnable], new ThreadFactory {
            override def newThread(r: Runnable): Thread = new Thread(r) {
              setUncaughtExceptionHandler(h)
            }
          }))
      } ! 1
      assertCountDown(l)
    }
  }

  private def assertCountDown(latch: CountDownLatch, timeout: Long = 1000): Boolean =
    latch.await(timeout, TimeUnit.MILLISECONDS) must_== true
}
