package com.github.plokhotnyuk.actors

import com.github.plokhotnyuk.actors.BenchmarkSpec._
import com.sun.management.OperatingSystemMXBean
import java.util.concurrent._
import java.lang.management.ManagementFactory._
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mutable.Specification
import org.specs2.specification.{Example, Step, Fragments}
import org.specs2.execute.Success
import scala.concurrent.forkjoin.{ForkJoinWorkerThread => ScalaForkJoinWorkerThread, ForkJoinPool => ScalaForkJoinPool}
import jsr166e.{ForkJoinWorkerThread => JSR166eForkJoinWorkerThread, ForkJoinPool => JSR166eForkJoinPool}

@RunWith(classOf[JUnitRunner])
abstract class BenchmarkSpec extends Specification {
  sequential
  xonly

  implicit def anyToSuccess(a: Any) = Success()

  override def map(fs: => Fragments) = Step(setup()) ^ fs.map {
    case Example(desc, body, _, _, _) => Example(desc, {
      println()
      println(s"$desc:")
      body()
    })
    case other => other
  } ^ Step(shutdown())

  def setup(): Unit = withSetup(println(s"Executor service type: $executorServiceType"))

  def shutdown()
}

object BenchmarkSpec {
  private val processors = Runtime.getRuntime.availableProcessors
  private val executorServiceType = System.getProperty("benchmark.executorServiceType", "scala-forkjoin-pool")
  private val poolSize = System.getProperty("benchmark.poolSize", processors.toString).toInt
  private val threadPriority = Option(System.getProperty("benchmark.threadPriority")).map(_.toInt)
  private val osMBean = newPlatformMXBeanProxy(getPlatformMBeanServer, OPERATING_SYSTEM_MXBEAN_NAME, classOf[OperatingSystemMXBean])
  
  val parallelism: Int = System.getProperty("benchmark.parallelism", processors.toString).toInt

  def roundToParallelism(n: Int): Int = (n / parallelism) * parallelism

  def createExecutorService(): ExecutorService = {
    def createJSR166eForkJoinWorkerThreadFactory() = new JSR166eForkJoinPool.ForkJoinWorkerThreadFactory {
      def newThread(pool: JSR166eForkJoinPool) = new JSR166eForkJoinWorkerThread(pool) {
        override def run(): Unit = withSetup(super.run())
      }
    }

    def createScalaForkJoinWorkerThreadFactory() = new ScalaForkJoinPool.ForkJoinWorkerThreadFactory {
      def newThread(pool: ScalaForkJoinPool) = new ScalaForkJoinWorkerThread(pool) {
        override def run(): Unit = withSetup(super.run())
      }
    }

    def createJavaForkJoinWorkerThreadFactory() = new ForkJoinPool.ForkJoinWorkerThreadFactory {
      def newThread(pool: ForkJoinPool) = new ForkJoinWorkerThread(pool) {
        override def run(): Unit = withSetup(super.run())
      }
    }

    def createThreadFactory() = new ThreadFactory {
      override def newThread(r: Runnable): Thread = new Thread {
        override def run(): Unit = withSetup(r.run())
      }
    }

    executorServiceType match {
      case "jsr166e-forkjoin-pool" => new JSR166eForkJoinPool(poolSize, createJSR166eForkJoinWorkerThreadFactory(), null, true)
      case "scala-forkjoin-pool" => new ScalaForkJoinPool(poolSize, createScalaForkJoinWorkerThreadFactory(), null, true)
      case "java-forkjoin-pool" => new ForkJoinPool(poolSize, createJavaForkJoinWorkerThreadFactory(), null, true)
      case "thread-pool" => new ThreadPoolExecutor(poolSize, poolSize, 60, TimeUnit.SECONDS,
        new LinkedBlockingQueue[Runnable](), createThreadFactory(), new ThreadPoolExecutor.DiscardPolicy())
      case _ => throw new IllegalArgumentException("Unsupported value of benchmark.executorServiceType property")
    }
  }

  def timed[A](n: Int, printAvgLatency: Boolean = false)(benchmark: => A): A = {
    val t = System.nanoTime()
    val ct = osMBean.getProcessCpuTime
    val r = benchmark
    val cd = osMBean.getProcessCpuTime - ct
    val d = System.nanoTime() - t
    println(f"$n%,d ops")
    println(f"$d%,d ns")
    if (printAvgLatency) println(f"${d / n}%,d ns/op")
    else println(f"${(n * 1000000000L) / d}%,d ops/s")
    println(f"${(cd * 100.0) / d / processors}%2.1f %% of CPU usage")
    r
  }

  def footprintedAndTimed[A](n: Int)(benchmark: => A): A = {
    val u = usedMemory
    val r = timed(n)(benchmark)
    val m = usedMemory - u
    val b = bytesPerInstance(m, n)
    println(f"$b%,d bytes per instance")
    r
  }

  def footprintedAndTimedCollect[A](n: Int)(construct: () => A): Seq[A] = {
    val r = Array.ofDim(n).asInstanceOf[Array[A]]
    val u = usedMemory
    timed(n, printAvgLatency = true) {
      val as = r
      var i = n
      while (i > 0) {
        i -= 1
        as(i) = construct()
      }
    }
    val m = usedMemory - u
    val b = bytesPerInstance(m, n)
    println(f"$b%,d bytes per instance")
    r
  }

  def bytesPerInstance(m: Long, n: Int): Int = Math.round(m.toDouble / n / 4).toInt * 4

  def usedMemory: Long = {
    def usage: Long = Runtime.getRuntime.totalMemory() - Runtime.getRuntime.freeMemory()

    @annotation.tailrec
    def forceGC(prevUsage: Long = usage): Long = {
      System.gc()
      Thread.sleep(10)
      val currUsage = usage
      if (currUsage >= prevUsage) forceGC(prevUsage)
      else currUsage
    }

    @annotation.tailrec
    def fullGC(precision: Double, prevUsage: Long = forceGC()): Long = {
      System.gc()
      Thread.sleep(10)
      val currUsage = usage
      if (Math.abs(prevUsage - currUsage).toDouble / prevUsage > precision) fullGC(precision, currUsage)
      else currUsage
    }

    fullGC(0.001)
  }

  def fork(code: => Unit): Unit =
    new Thread {
      override def run(): Unit = withSetup(code)
    }.start()

  def withSetup[A](a: => A): Unit = {
    threadPriority.foreach(setThreadPriority(Thread.currentThread(), _))
    a
  }

  def setThreadPriority(thread: Thread, priority: Int): Unit = {
    def ancestors(x: ThreadGroup): List[ThreadGroup] =
      Option(x.getParent).fold(List[ThreadGroup]())(x :: ancestors(_))

    ancestors(thread.getThreadGroup).reverse.foreach(_.setMaxPriority(priority))
    thread.setPriority(priority)
  }
  
  def fullShutdown(e: ExecutorService): Unit = {
    e.shutdownNow()
    e.awaitTermination(0, TimeUnit.SECONDS)
  }
}
