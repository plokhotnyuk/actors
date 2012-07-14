package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mutable.Specification
import org.specs2.execute.{Success, Result}
import akka.jsr166y.ForkJoinPool
import org.specs2.specification.{Fragments, Example}

@RunWith(classOf[JUnitRunner])
abstract class BenchmarkSpec extends Specification {
  val CPUs = Runtime.getRuntime.availableProcessors()

  sequential
  xonly

  override def map(fs: => Fragments) = fs.map {
    case Example(desc, body) => Example(desc.toString, { printf("\n%s:\n", desc); body() })
    case other => other
  }

  def fifoForkJoinPool(parallelism: Int): ForkJoinPool =
    new ForkJoinPool(parallelism, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true)

  def lifoForkJoinPool(parallelism: Int): ForkJoinPool =
    new ForkJoinPool(parallelism, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, false)

  def timed(n: Int)(benchmark: => Unit): Result = {
    val t = System.nanoTime
    benchmark
    val d = System.nanoTime - t
    printf("%,d ns\n%,d ops\n%,d ns/op\n%,d ops/s\n", d, n, d / n, (n * 1000000000L) / d)
    Success()
  }

  def fork(code: => Unit): Thread = {
    val t = new Thread {
      override def run() {
        code
      }
    }
    t.start()
    t
  }
}
