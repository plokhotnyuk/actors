package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mutable.Specification
import org.specs2.execute.{Success, Result}
import org.specs2.specification.{Step, Fragments, Example}
import concurrent.forkjoin.{ForkJoinWorkerThread => ScalaForkJoinWorkerThread, ForkJoinPool => ScalaForkJoinPool}
import java.util.concurrent._
import com.higherfrequencytrading.affinity.AffinitySupport
import com.github.plokhotnyuk.actors.BenchmarkSpec._
import com.higherfrequencytrading.affinity.impl.{PosixJNAAffinity, WindowsJNAAffinity, NativeAffinity}
import java.lang.management.ManagementFactory._
import com.sun.management.OperatingSystemMXBean

@RunWith(classOf[JUnitRunner])
abstract class BenchmarkSpec extends Specification {
  sequential
  xonly

  override def map(fs: => Fragments) = Step(setup()) ^ fs.map {
    case Example(desc, body) => Example(desc.toString, {
      println()
      println(s"$desc:")
      body()
    })
    case other => other
  } ^ Step(shutdown())

  def setup() {
    println(s"Executor service type: $executorServiceType")
    threadSetup()
  }

  def shutdown()
}

object BenchmarkSpec {
  val processors = Runtime.getRuntime.availableProcessors
  val executorServiceType = System.getProperty("benchmark.executorServiceType", "scala-forkjoin-pool")
  val parallelism = System.getProperty("benchmark.parallelism", processors.toString).toInt
  val poolSize = System.getProperty("benchmark.poolSize", processors.toString).toInt
  val threadPriority = System.getProperty("benchmark.threadPriority", Thread.currentThread().getPriority.toString).toInt
  val isAffinityOn = System.getProperty("benchmark.affinityOn", "false").toBoolean
  if (isAffinityOn) println(s"Using $affinityType affinity control implementation")
  val printBinding = System.getProperty("benchmark.printBinding", "false").toBoolean
  val osMBean = newPlatformMXBeanProxy(getPlatformMBeanServer, OPERATING_SYSTEM_MXBEAN_NAME, classOf[OperatingSystemMXBean])
  var cpuId: Int = 0

  def affinityType: String =
    if (NativeAffinity.LOADED) "JNI-based"
    else if (NativeAffinity.isWindows && AffinitySupport.isJNAAvailable && WindowsJNAAffinity.LOADED) "Windows JNA-based"
    else if (AffinitySupport.isJNAAvailable && PosixJNAAffinity.LOADED) "Posix JNA-based"
    else "dummy"

  def createExecutorService(): ExecutorService = {
    def createScalaForkJoinWorkerThreadFactory() = new ScalaForkJoinPool.ForkJoinWorkerThreadFactory {
      def newThread(pool: ScalaForkJoinPool) = new ScalaForkJoinWorkerThread(pool) {
        override def run() {
          threadSetup()
          super.run()
        }
      }
    }

    def createJavaForkJoinWorkerThreadFactory() = new ForkJoinPool.ForkJoinWorkerThreadFactory {
      def newThread(pool: ForkJoinPool) = new ForkJoinWorkerThread(pool) {
        override def run() {
          threadSetup()
          super.run()
        }
      }
    }

    def createThreadFactory() = new ThreadFactory {
      override def newThread(r: Runnable): Thread = new Thread {
        override def run() {
          threadSetup()
          r.run()
        }
      }
    }

    executorServiceType match {
      case "scala-forkjoin-pool" => new ScalaForkJoinPool(poolSize, createScalaForkJoinWorkerThreadFactory(), null, true)
      case "java-forkjoin-pool" => new ForkJoinPool(poolSize, createJavaForkJoinWorkerThreadFactory(), null, true)
      case "fixed-thread-pool" => new FixedThreadPoolExecutor(poolSize, createThreadFactory(), onReject = _ => ())
      case "thread-pool" => new ThreadPoolExecutor(poolSize, poolSize, 60, TimeUnit.SECONDS,
        new LinkedBlockingQueue[Runnable](), createThreadFactory(), new ThreadPoolExecutor.DiscardPolicy())
      case _ => throw new IllegalArgumentException("Unsupported value of benchmark.executorServiceType property")
    }
  }

  def timed(n: => Int)(benchmark: => Unit): Result = {
    val t = System.currentTimeMillis * 1000000L
    val ct = osMBean.getProcessCpuTime
    benchmark
    val cd = osMBean.getProcessCpuTime - ct
    val d = System.currentTimeMillis * 1000000L - t
    println(f"$n%,d ops")
    println(f"${d / 1000000L}%,d ms")
    println(f"${d / n}%,d ns/op")
    println(f"${(n * 1000000000L) / d}%,d ops/s")
    println(f"${(cd * 100.0) / d / processors}%2.1f %% of CPU usage")
    Success()
  }

  def fork(code: => Unit) {
    new Thread {
      override def run() {
        threadSetup()
        code
      }
    }.start()
  }

  def threadSetup() {
    def setThreadPriority(priority: Int) {
      def ancestors(thread: ThreadGroup, acc: List[ThreadGroup] = Nil): List[ThreadGroup] =
        if (thread.getParent != null) ancestors(thread.getParent, thread :: acc) else acc

      val thread = Thread.currentThread()
      ancestors(thread.getThreadGroup).foreach(_.setMaxPriority(priority))
      thread.setPriority(priority)
    }

    def setThreadAffinity() {
      synchronized {
        AffinitySupport.setAffinity(1L << cpuId)
        if (printBinding) {
          val thread = Thread.currentThread()
          println(s"CPU[$cpuId]: '${thread.getName}' with priority: ${thread.getPriority}")
        }
        cpuId = (cpuId + 1) % Runtime.getRuntime.availableProcessors
      }
    }

    setThreadPriority(threadPriority)
    if (isAffinityOn) setThreadAffinity()
  }
}