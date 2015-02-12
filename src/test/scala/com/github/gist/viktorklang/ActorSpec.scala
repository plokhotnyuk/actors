package com.github.gist.viktorklang

import java.util.concurrent._
import org.specs2.mutable.Specification
import com.github.gist.viktorklang.Actor._

class ActorSpec extends Specification {
  args(sequential = true)

  val NumOfMessages = 1000000
  val NumOfThreads = 4

  "actor with Scala fork-join pool executor" should {
    implicit val e = new scala.concurrent.forkjoin.ForkJoinPool()
    actorTests(NumOfMessages)
  }

  "actor with Java fork-join pool executor" should {
    implicit val e = new ForkJoinPool()
    actorTests(NumOfMessages)
  }

  "actor with fixed thread pool executor" should {
    implicit val e = Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors, new ThreadFactory {
      val defaultThreadFactory = Executors.defaultThreadFactory()

      def newThread(r: Runnable) = {
        val t = defaultThreadFactory.newThread(r)
        t.setDaemon(true)
        t
      }
    })
    actorTests(NumOfMessages)
  }

  def actorTests(n: Int)(implicit e: Executor) = {
    "execute code async" in {
      val l = new CountDownLatch(1)
      Actor(_ => _ => {
        l.countDown()
        Stay
      }) ! 1
      assertCountDown(l)
    }

    "exchange messages with another actor without loss" in {
      val l = new CountDownLatch(n)
      lazy val a1: Address = Actor(_ => {
        case i: Int =>
          if (i == l.getCount) {
            if (i != 0) a2 ! i - 1
            l.countDown()
            l.countDown()
          }
          Stay
      })
      lazy val a2 = Actor(_ => {
        case i: Int =>
          a1 ! i - 1
          Stay
      })
      a1 ! n
      assertCountDown(l)
    }

    "create child actor and send messages to it recursively" in {
      val l = new CountDownLatch(1)

      def a: Address = Actor(_ => {
        case i: Int =>
          if (i > 0) a ! i - 1 else l.countDown()
          Stay
      })

      a ! n
      assertCountDown(l)
    }

    "handle messages in order of sending by each thread" in {
      val nRounded = (n / NumOfThreads) * NumOfThreads
      val l = new CountDownLatch(nRounded)
      val ms = collection.mutable.Map[Int, Int]()
      val a = Actor(_ => {
        case m: (Int, Int) @unchecked =>
          val (j, i) = m
          if (ms.getOrElse(j, 0) + 1 == i) {
            ms.put(j, i)
            l.countDown()
          }
          Stay
      })
      for (j <- 1 to NumOfThreads) fork {
        for (i <- 1 to nRounded / NumOfThreads) a ! j -> i
      }
      assertCountDown(l)
    }

    "doesn't handle messages in simultaneous threads" in {
      val nRounded = (n / NumOfThreads) * NumOfThreads
      val l = new CountDownLatch(1)
      val expectedSum = nRounded * (nRounded + 1L) / 2

      def accumulator(m: Any, sum: Long = 0L): Effect = m match {
        case i: Int =>
          if (sum + i == expectedSum) l.countDown()
          Become(m => accumulator(m, sum + i))
      }

      val a = Actor(_ => m => accumulator(m))
      val nPerThread = nRounded / NumOfThreads
      for (j <- 1 to NumOfThreads) fork {
        val off = (j - 1) * nPerThread
        for (i <- 1 to nPerThread) a ! i + off
      }
      assertCountDown(l)
    }

    "redirect unhandled errors to uncaught exception handler of thread" in {
      val l = new CountDownLatch(1)
      val err = System.err
      try {
        System.setErr(new java.io.PrintStream(new java.io.OutputStream {
          override def write(b: Int): Unit = l.countDown()
        }))
        Actor(_ => _ => {
          throw null
          Stay
        }) ! 1
        assertCountDown(l)
      } finally System.setErr(err)
    }

    "handle messages with previous behaviour after unhandled errors" in {
      val l = new CountDownLatch(1)
      val err = System.err
      try {
        System.setErr(new java.io.PrintStream(new java.io.OutputStream {
          override def write(b: Int): Unit = () // ignore err output that will be flooded with stack traces
        }))
        val q = 1000 // 1 / frequency of exceptions
        val expectedSum = n * (n + 1L) / 2 - q * (n / q * (n / q + 1L) / 2)

        def failingAccumulator(m: Any, sum: Long = 0L): Effect = m match {
          case i: Int =>
            if (i % q == 0) {
              throw null
              Die // should never happen
            } else {
              if (sum + i == expectedSum) l.countDown()
              Become(m => failingAccumulator(m, sum + i))
            }
        }

        val a = Actor(_ => m => failingAccumulator(m))
        for (i <- 1 to n) {
          a ! i
        }
        assertCountDown(l)
      } finally System.setErr(err)
    }
  }

  private def assertCountDown(l: CountDownLatch, timeout: Long = 1000): Boolean =
    l.await(timeout, TimeUnit.MILLISECONDS) must_== true

  private def fork(f: => Unit): Unit = new Thread {
    override def run(): Unit = f
  }.start()
}
