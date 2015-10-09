package com.github.plokhotnyuk.actors

import com.github.plokhotnyuk.actors.BenchmarkSpec._
import java.util.concurrent.{ForkJoinPool, CountDownLatch}
import net.liftweb.actor.{ILAExecute, LAScheduler, LiftActor}
import net.liftweb.common.Full
import org.specs2.execute.Success

class LiftActorSpec extends BenchmarkSpec {
  LAScheduler.createExecutor = () => new ILAExecute {
    private val executorService = createExecutorService()

    def execute(f: () => Unit): Unit = executorService match {
      case p: scala.concurrent.forkjoin.ForkJoinPool => new ScalaForkJoinTask(p) {
        def exec(): Boolean = {
          f()
          false
        }
      }
      case p: ForkJoinPool => new JavaForkJoinTask(p) {
        def exec(): Boolean = {
          f()
          false
        }
      }
      case p => p.execute(new Runnable {
        def run(): Unit = f()
      })
    }

    def shutdown(): Unit = fullShutdown(executorService)
  }

  "Enqueueing" in {
    val n = 10000000
    val l1 = new CountDownLatch(1)
    val l2 = new CountDownLatch(1)
    val a = blockableCountActor(l1, l2, n)
    footprintedAndTimed(n) {
      sendMessages(a, n)
    }
    l1.countDown()
    l2.await()
    Success()
  }

  "Dequeueing" in {
    val n = 10000000
    val l1 = new CountDownLatch(1)
    val l2 = new CountDownLatch(1)
    val a = blockableCountActor(l1, l2, n)
    sendMessages(a, n)
    timed(n) {
      l1.countDown()
      l2.await()
    }
    Success()
  }

  "Initiation" in {
    footprintedAndTimedCollect(10000000)(() => new LiftActor {
      def messageHandler: PartialFunction[Any, Unit] = {
        case _ =>
      }
    })
    Success()
  }

  "Single-producer sending" in {
    val n = 6000000
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    timed(n) {
      sendMessages(a, n)
      l.await()
    }
    Success()
  }

  "Multi-producer sending" in {
    val n = roundToParallelism(6000000)
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    val r = new ParRunner((1 to parallelism).map(_ => () => sendMessages(a, n / parallelism)))
    timed(n) {
      r.start()
      l.await()
    }
    Success()
  }

  "Max throughput" in {
    val n = roundToParallelism(12000000)
    val l = new CountDownLatch(parallelism)
    val r = new ParRunner((1 to parallelism).map {
      _ =>
        val a = countActor(l, n / parallelism)
        () => sendMessages(a, n / parallelism)
    })
    timed(n) {
      r.start()
      l.await()
    }
    Success()
  }

  "Ping latency" in {
    pingLatency(1500000)
    Success()
  }

  "Ping throughput 10K" in {
    pingThroughput(2000000, 10000)
    Success()
  }

  def shutdown(): Unit = LAScheduler.shutdown()

  private def pingLatency(n: Int): Unit = {
    latencyTimed(n) {
      h =>
        val l = new CountDownLatch(2)
        var a1: LiftActor = null
        val a2 = new LiftActor {
          private var i = n / 2

          override val highPriorityReceive = Full[PartialFunction[Any, Unit]]({
            case m =>
              h.record()
              if (i > 0) a1 ! m
              i -= 1
              if (i == 0) l.countDown()
          })

          def messageHandler: PartialFunction[Any, Unit] = {
            case _ =>
          }
        }
        a1 = new LiftActor {
          private var i = n / 2

          override val highPriorityReceive = Full[PartialFunction[Any, Unit]]({
            case m =>
              h.record()
              if (i > 0) a2 ! m
              i -= 1
              if (i == 0) l.countDown()
          })

          def messageHandler: PartialFunction[Any, Unit] = {
            case _ =>
          }
        }
        a2 ! Message()
        l.await()
    }
  }

  private def pingThroughput(n: Int, p: Int): Unit = {
    val l = new CountDownLatch(p * 2)
    val as = (1 to p).map {
      _ =>
        var a1: LiftActor = null
        val a2 = new LiftActor {
          private var i = n / p / 2

          override val highPriorityReceive = Full[PartialFunction[Any, Unit]]({
            case m =>
              if (i > 0) a1 ! m
              i -= 1
              if (i == 0) l.countDown()
          })

          def messageHandler: PartialFunction[Any, Unit] = {
            case _ =>
          }
        }
        a1 = new LiftActor {
          private var i = n / p / 2

          override val highPriorityReceive = Full[PartialFunction[Any, Unit]]({
            case m =>
              if (i > 0) a2 ! m
              i -= 1
              if (i == 0) l.countDown()
          })

          def messageHandler: PartialFunction[Any, Unit] = {
            case _ =>
          }
        }
        a2
    }
    timed(n) {
      as.foreach(_ ! Message())
      l.await()
    }
  }

  private def blockableCountActor(l1: CountDownLatch, l2: CountDownLatch, n: Int): LiftActor =
    new LiftActor {
      private var blocked = true
      private var i = n - 1

      override val highPriorityReceive = Full[PartialFunction[Any, Unit]]({
        case _ =>
          if (blocked) {
            l1.await()
            blocked = false
          } else {
            i -= 1
            if (i == 0) l2.countDown()
          }
      })

      def messageHandler: PartialFunction[Any, Unit] = {
        case _ =>
      }
    }

  private def countActor(l: CountDownLatch, n: Int): LiftActor =
    new LiftActor {
      private var i = n

      override val highPriorityReceive = Full[PartialFunction[Any, Unit]]({
        case _ =>
          i -= 1
          if (i == 0) l.countDown()
      })

      def messageHandler: PartialFunction[Any, Unit] = {
        case _ =>
      }
    }

  private def sendMessages(a: LiftActor, n: Int): Unit = {
    val m = Message()
    var i = n
    while (i > 0) {
      a ! m
      i -= 1
    }
  }
}
