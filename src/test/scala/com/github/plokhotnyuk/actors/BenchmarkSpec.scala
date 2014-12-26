package com.github.plokhotnyuk.actors

import akka.dispatch.ForkJoinExecutorConfigurator.AkkaForkJoinPool
import com.github.plokhotnyuk.actors.BenchmarkSpec._
import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory._
import java.util.concurrent._
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mutable.Specification
import org.specs2.specification.{Example, Step, Fragments}
import scala.concurrent.forkjoin.{ForkJoinPool => ScalaForkJoinPool}

@RunWith(classOf[JUnitRunner])
abstract class BenchmarkSpec extends Specification {
  sequential
  xonly

  override def map(fs: => Fragments) = Step(setup()) ^ fs.map {
    case Example(desc, body, _, _, _) => Example(desc, {
      println()
      usedMemory() // GC
      println(s"$desc:")
      body()
    })
    case other => other
  } ^ Step(shutdown())

  def setup(): Unit = println(s"Executor service type: $executorServiceType")

  def shutdown(): Unit
}

object BenchmarkSpec {
  private val processors = Runtime.getRuntime.availableProcessors
  private val executorServiceType = System.getProperty("benchmark.executorServiceType", "scala-forkjoin-pool")
  private val poolSize = System.getProperty("benchmark.poolSize", processors.toString).toInt
  private val osMBean = newPlatformMXBeanProxy(getPlatformMBeanServer, OPERATING_SYSTEM_MXBEAN_NAME, classOf[OperatingSystemMXBean])
  
  val parallelism: Int = System.getProperty("benchmark.parallelism", processors.toString).toInt

  def roundToParallelism(n: Int): Int = (n / parallelism) * parallelism

  def createExecutorService(size: Int = poolSize): ExecutorService =
    executorServiceType match {
      case "akka-forkjoin-pool" => new AkkaForkJoinPool(size, ScalaForkJoinPool.defaultForkJoinWorkerThreadFactory, null)
      case "scala-forkjoin-pool" => new ScalaForkJoinPool(size, ScalaForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true)
      case "java-forkjoin-pool" => new ForkJoinPool(size, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true)
      case "lbq-thread-pool" => new ThreadPoolExecutor(size, size, 1, TimeUnit.HOURS,
        new LinkedBlockingQueue[Runnable](), Executors.defaultThreadFactory(), new ThreadPoolExecutor.DiscardPolicy())
      case "abq-thread-pool" => new ThreadPoolExecutor(size, size, 1, TimeUnit.HOURS,
        new ArrayBlockingQueue[Runnable](100000), Executors.defaultThreadFactory(), new ThreadPoolExecutor.DiscardPolicy())
      case _ => throw new IllegalArgumentException("Unsupported value of benchmark.executorServiceType property")
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
    val u = usedMemory()
    val r = timed(n)(benchmark)
    val m = usedMemory() - u
    val b = bytesPerInstance(m, n)
    println(f"$b%,d bytes per instance")
    r
  }

  def footprintedAndTimedCollect[A](n: Int)(construct: () => A): Seq[A] = {
    val r = Array.ofDim(n).asInstanceOf[Array[A]]
    val u = usedMemory()
    timed(n, printAvgLatency = true) {
      val as = r
      var i = n
      while (i > 0) {
        i -= 1
        as(i) = construct()
      }
    }
    val m = usedMemory() - u
    val b = bytesPerInstance(m, n)
    println(f"$b%,d bytes per instance")
    r
  }

  def bytesPerInstance(m: Long, n: Int): Int = Math.round(m.toDouble / n / 4).toInt * 4

  def usedMemory(precision: Double = 0.01): Long = {
    @annotation.tailrec
    def waitForGCCompleting(prevUsage: Long = 0): Long = {
      Thread.sleep(30)
      val usage = Runtime.getRuntime.totalMemory() - Runtime.getRuntime.freeMemory()
      val diff = prevUsage - usage
      if (diff < 0) {
        System.gc()
        waitForGCCompleting(usage)
      } else if (diff.toDouble / prevUsage > precision) waitForGCCompleting(usage)
      else usage
    }

    System.gc()
    waitForGCCompleting()
  }

  def fullShutdown(e: ExecutorService): Unit = {
    e.shutdownNow()
    e.awaitTermination(0, TimeUnit.SECONDS)
  }
}

class ParRunner(fs: Seq[() => Unit]) {
  val barrier = new CyclicBarrier(fs.size + 1)
  fs.map(f => new Thread {
    override def run(): Unit = {
      barrier.await()
      f()
    }
  }).foreach(_.start())

  def start(): Unit = barrier.await()
}
