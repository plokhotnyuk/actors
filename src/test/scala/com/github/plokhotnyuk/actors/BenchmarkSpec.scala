package com.github.plokhotnyuk.actors

import javax.management.openmbean.CompositeData
import javax.management.{Notification, NotificationListener, NotificationEmitter}

import akka.dispatch.ForkJoinExecutorConfigurator.AkkaForkJoinPool
import com.github.plokhotnyuk.actors.BenchmarkSpec._
import com.sun.management.{GarbageCollectionNotificationInfo, OperatingSystemMXBean}
import java.lang.management.ManagementFactory._
import java.util.concurrent._
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mutable.Specification
import org.specs2.specification.{Example, Step, Fragments}
import scala.collection.convert.wrapAsScala._
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
  private val osMXBean = newPlatformMXBeanProxy(getPlatformMBeanServer, OPERATING_SYSTEM_MXBEAN_NAME, classOf[OperatingSystemMXBean])
  private val memoryMXBean = getMemoryMXBean
  private val gcMXBeans: Array[NotificationEmitter] = getGarbageCollectorMXBeans.toArray.map((b: Any) => b.asInstanceOf[NotificationEmitter])

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
        new ArrayBlockingQueue[Runnable](1000000), Executors.defaultThreadFactory(), new ThreadPoolExecutor.DiscardPolicy())
      case _ => throw new IllegalArgumentException("Unsupported value of benchmark.executorServiceType property")
    }

  def timed[A](n: Int, printAvgLatency: Boolean = false)(benchmark: => A): A = {
    val t = System.nanoTime()
    val ct = osMXBean.getProcessCpuTime
    val r = benchmark
    val cd = osMXBean.getProcessCpuTime - ct
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

  def bytesPerInstance(m: Long, n: Int): Int = Math.round(m.toDouble / n).toInt

  def usedMemory(precision: Double = 0.000001): Long = {
    def fullGC(): Unit = {
      val l = new CountDownLatch(1)
      val enls = gcMXBeans.map {
        e =>
          val nl = new NotificationListener() {
            def handleNotification(notification: Notification, handback: Any): Unit = {
              if (notification.getType == GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION) {
                val info = GarbageCollectionNotificationInfo.from(notification.getUserData.asInstanceOf[CompositeData])
                if (info.getGcAction.contains("major")) l.countDown()
              }
            }
          }
          e.addNotificationListener(nl, null, null)
          (e, nl)
      }
      System.gc()
      try l.await(10, TimeUnit.SECONDS) finally enls.foreach {
        case (e, nl) => e.removeNotificationListener(nl)
      }
    }

    @annotation.tailrec
    def waitForGCCompleting(prevUsage: Long = Long.MaxValue): Long = {
      Thread.sleep(30)
      val usage = memoryMXBean.getHeapMemoryUsage.getUsed
      val diff = prevUsage - usage
      if (diff < 0 || diff.toDouble / usage > precision) {
        fullGC()
        waitForGCCompleting(usage)
      } else usage
    }

    fullGC()
    waitForGCCompleting()
  }

  def fullShutdown(e: ExecutorService): Unit = {
    e.shutdownNow()
    e.awaitTermination(0, TimeUnit.SECONDS)
  }
}
