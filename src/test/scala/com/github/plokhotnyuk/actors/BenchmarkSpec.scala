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
  var executorServiceType = System.getProperty("benchmark.executorServiceType", "scala-forkjoin-pool")
  var parallelism = System.getProperty("benchmark.parallelism", Runtime.getRuntime.availableProcessors.toString).toInt
  var threadPriority = System.getProperty("benchmark.threadPriority", Thread.currentThread().getPriority.toString).toInt
  var isAffinityOn = System.getProperty("benchmark.affinityOn", "false").toBoolean
  if (isAffinityOn) println(s"Using $affinityType affinity control implementation")
  var printBinding = System.getProperty("benchmark.printBinding", "false").toBoolean
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
      case "scala-forkjoin-pool" => new ScalaForkJoinPool(parallelism, createScalaForkJoinWorkerThreadFactory(), null, true)
      case "java-forkjoin-pool" => new ForkJoinPool(parallelism, createJavaForkJoinWorkerThreadFactory(), null, true)
      case "fast-thread-pool" => new FastThreadPoolExecutor(parallelism, createThreadFactory())
      case "thread-pool" => new ThreadPoolExecutor(parallelism, parallelism, 60, TimeUnit.SECONDS,
        new LinkedBlockingQueue[Runnable](), createThreadFactory())
      case _ => throw new IllegalArgumentException("Unsupported value of benchmark.executorServiceType property")
    }
  }

  def timed(n: => Int)(benchmark: => Unit): Result = {
    val t = System.nanoTime
    benchmark
    val d = System.nanoTime - t
    println(f"$n%,d ops")
    println(f"$d%,d ns")
    println(f"${d / n}%,d ns/op")
    println(f"${(n * 1000000000L) / d}%,d ops/s")
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