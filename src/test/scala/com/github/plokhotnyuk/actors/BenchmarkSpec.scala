package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mutable.Specification
import org.specs2.execute.{Success, Result}
import org.specs2.specification.{Step, Fragments, Example}
import concurrent.forkjoin.{ForkJoinWorkerThread, ForkJoinPool}
import java.util.concurrent._
import atomic.AtomicInteger
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
    tryRestrictCurrentThreadToNextCPU()
  }

  def shutdown() {
    // do nothing
  }
}

object BenchmarkSpec {
  val isAffinityOn = false
  val CPUs = Runtime.getRuntime.availableProcessors
  val affinityService = ThreadAffinityUtils.defaultAffinityService
  val layout = ThreadAffinityUtils.defaultLayoutService
  var lastCpuNum = new AtomicInteger()

  val forkJoinWorkerThreadFactory = new ForkJoinPool.ForkJoinWorkerThreadFactory {
    def newThread(pool: ForkJoinPool): ForkJoinWorkerThread = new ForkJoinWorkerThread(pool) {
      override def onStart() {
        tryRestrictCurrentThreadToNextCPU()
      }
    }
  }

  val threadFactory = new ThreadFactory {
    override def newThread(r: Runnable): Thread = new Thread {
      override def run() {
        tryRestrictCurrentThreadToNextCPU()
        r.run()
      }
    }
  }

  def fifoForkJoinPool(parallelism: Int): ExecutorService =
    new ForkJoinPool(parallelism, forkJoinWorkerThreadFactory, null, true)

  def lifoForkJoinPool(parallelism: Int): ExecutorService =
    new ForkJoinPool(parallelism, forkJoinWorkerThreadFactory, null, false)

  def fixedThreadPool(parallelism: Int): ExecutorService =
    new ThreadPoolExecutor(parallelism, parallelism, 60, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable](),
      threadFactory, new ThreadPoolExecutor.AbortPolicy())

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
        tryRestrictCurrentThreadToNextCPU()
        code
      }
    }.start()
  }

  def tryRestrictCurrentThreadToNextCPU() {
    val cpuName = if (isAffinityOn) {
      val cpuNum = lastCpuNum.getAndIncrement() % CPUs
      affinityService.restrictCurrentThreadTo(layout.cpu(cpuNum))
      cpuNum.toString
    } else {
      "?"
    }
    //println("CPU[" + cpuName + "]: " + Thread.currentThread().getName)
  }
}
