package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mutable.Specification
import org.specs2.execute.{Success, Result}
import org.specs2.specification.{Step, Fragments, Example}
import concurrent.forkjoin.{ForkJoinWorkerThread, ForkJoinPool}
import java.util.concurrent._
import com.affinity.ThreadAffinityUtils
import com.github.plokhotnyuk.actors.BenchmarkSpec._

@RunWith(classOf[JUnitRunner])
abstract class BenchmarkSpec extends Specification {
  sequential
  xonly

  override def map(fs: => Fragments) = Step(setup()) ^ fs.map {
    case Example(desc, body) => Example(desc.toString, { printf("\n%s:\n", desc); body() })
    case other => other
  } ^ Step(shutdown())

  def setup() {
    onStart()
  }

  def shutdown() {
    onStop()
  }
}

object BenchmarkSpec {
  val isAffinityOn = System.getProperty("benchmark.affinityOn", "false").toBoolean
  val printBinding = System.getProperty("benchmark.printBinding", "false").toBoolean
  val parallelism = System.getProperty("benchmark.parallelism", Runtime.getRuntime.availableProcessors.toString).toInt
  val affinityService = ThreadAffinityUtils.defaultAffinityService
  val layout = ThreadAffinityUtils.defaultLayoutService
  val cpuBindings = Array.ofDim[Int](Runtime.getRuntime.availableProcessors)

  def createExecutorService(): ExecutorService = {
    def createForkJoinWorkerThreadFactory() = new ForkJoinPool.ForkJoinWorkerThreadFactory {
      def newThread(pool: ForkJoinPool): ForkJoinWorkerThread = new ForkJoinWorkerThread(pool) {
        override def run() {
          onStart()
          try {
            super.run()
          } finally {
            onStop()
          }
        }
      }
    }

    def createThreadFactory() = new ThreadFactory {
      override def newThread(r: Runnable): Thread = new Thread {
        override def run() {
          onStart()
          try {
            r.run()
          } finally {
            onStop()
          }
        }
      }
    }

    System.getProperty("benchmark.executorService", "lifo-forkjoin-pool") match {
      case "fifo-forkjoin-pool" => new ForkJoinPool(parallelism, createForkJoinWorkerThreadFactory(), null, true)
      case "lifo-forkjoin-pool" => new ForkJoinPool(parallelism, createForkJoinWorkerThreadFactory(), null, false)
      case "fixed-thread-pool" => new ThreadPoolExecutor(parallelism, parallelism, 60, TimeUnit.SECONDS,
        new LinkedBlockingQueue[Runnable](), createThreadFactory(), new ThreadPoolExecutor.AbortPolicy())
      case _ => throw new IllegalArgumentException("Unsupported executorService")
    }
  }

  def timed(n: Int)(benchmark: => Unit): Result = {
    val t = System.nanoTime
    benchmark
    val d = System.nanoTime - t
    printf("%,d ns\n%,d ops\n%,d ns/op\n%,d ops/s\n", d, n, d / n, (n * 1000000000L) / d)
    Success()
  }

  def fork(code: => Unit) {
    new Thread {
      override def run() {
        onStart()
        try {
          code
        } finally {
          onStop()
        }
      }
    }.start()
  }

  def onStart() {
    val cpuName = if (isAffinityOn) {
      val cpuNum = synchronized {
        val n = cpuBindings.indexOf(cpuBindings.min)
        cpuBindings(n) = cpuBindings(n) + 1
        n
      }
      affinityService.restrictCurrentThreadTo(layout.cpu(cpuNum))
      cpuNum.toString
    } else "*"
    if (printBinding) println("CPU[" + cpuName + "]: " + Thread.currentThread().getName)
  }

  def onStop() {
    if (isAffinityOn) {
      synchronized {
        val n = affinityService.currentThreadCPU().id
        cpuBindings(n) = cpuBindings(n) - 1
      }
    }
  }
}
