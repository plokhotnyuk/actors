package scalaz.concurrent

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger
import org.specs2.mutable.Specification
import scalaz.concurrent.Actor2._

class Actor2Spec extends Specification {
  args(sequential = true)

  val NumOfMessages = 1000000
  val NumOfThreads = 4

  "actor with sequential actor strategy" should {
    implicit val s = ActorStrategy.Sequential
    unboundedActorTests(NumOfMessages)
    boundedActorTests(NumOfMessages)
  }

  "actor with actor strategy backed by Scala fork-join pool" should {
    implicit val s = ActorStrategy.Executor(new scala.concurrent.forkjoin.ForkJoinPool())
    unboundedActorTests(NumOfMessages)
    boundedActorTests(NumOfMessages)
  }

  "actor with actor strategy backed by Java fork-join pool" should {
    implicit val s = ActorStrategy.Executor(new ForkJoinPool())
    unboundedActorTests(NumOfMessages)
    boundedActorTests(NumOfMessages)
  }

  "actor with actor strategy backed by fixed thread pool" should {
    implicit val s = ActorStrategy.Executor(Strategy.DefaultExecutorService)
    unboundedActorTests(NumOfMessages)
    boundedActorTests(NumOfMessages)
  }

  def unboundedActorTests(n: Int)(implicit s: ActorStrategy) = {
    "execute code async" in {
      val l = new CountDownLatch(1)
      unboundedActor((i: Int) => l.countDown()) ! 1
      assertCountDown(l)
    }

    "catch code errors that can be handled" in {
      val l = new CountDownLatch(1)
      unboundedActor((_: Int) => 1 / 0, (_: Throwable) => l.countDown()) ! 1
      assertCountDown(l)
    }

    "exchange messages with another actor without loss" in {
      val l = new CountDownLatch(n)
      var a1: Actor2[Int] = null
      val a2 = unboundedActor((i: Int) => a1 ! i - 1)
      a1 = unboundedActor {
        (i: Int) =>
          if (i == l.getCount) {
            if (i != 0) a2 ! i - 1
            l.countDown()
            l.countDown()
          }
      }
      a1 ! n
      assertCountDown(l)
    }

    "create child actor and send messages to it recursively" in {
      val l = new CountDownLatch(1)

      def a: Actor2[Int] = unboundedActor((i: Int) => if (i > 0) a ! i - 1 else l.countDown())

      a ! (if (s eq  ActorStrategy.Sequential) 300 else n)
      assertCountDown(l)
    }

    "handle messages in order of sending by each thread" in {
      val nRounded = (n / NumOfThreads) * NumOfThreads
      val l = new CountDownLatch(nRounded)
      val a = countingDownUnboundedActor(l)
      for (j <- 1 to NumOfThreads) fork {
        for (i <- 1 to nRounded / NumOfThreads) {
          a ! j -> i
        }
      }
      assertCountDown(l)
    }

    "doesn't handle messages in simultaneous threads" in {
      val nRounded = (n / NumOfThreads) * NumOfThreads
      val l = new CountDownLatch(1)
      val a = boundedActor(Int.MaxValue, {
        var sum = 0L
        val expectedSum = nRounded * (nRounded + 1L) / 2
        (i: Int) =>
          sum += i
          if (sum == expectedSum) l.countDown()
      })
      val numOfMessagesPerThread = nRounded / NumOfThreads
      for (j <- 1 to NumOfThreads) fork {
        val off = (j - 1) * numOfMessagesPerThread
        for (i <- 1 to numOfMessagesPerThread) {
          a ! i + off
        }
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
        unboundedActor((_: Int) => 1 / 0) ! 1
        assertCountDown(l)
      } catch {
        case e: ArithmeticException if e.getMessage == "/ by zero" =>
          l.countDown() // for a sequential strategy
          assertCountDown(l)
      } finally System.setErr(err)
    }
  }

  def boundedActorTests(n: Int)(implicit s: ActorStrategy) = {
    "execute code async" in {
      val l = new CountDownLatch(1)
      boundedActor(Int.MaxValue, (_: Int) => l.countDown()) ! 1
      assertCountDown(l)
    }

    "catch code errors that can be handled" in {
      val l = new CountDownLatch(1)
      boundedActor(Int.MaxValue, (_: Int) => 1 / 0, (_: Throwable) => l.countDown()) ! 1
      assertCountDown(l)
    }

    "exchange messages with another actor without loss" in {
      val l = new CountDownLatch(n)
      var a1: Actor2[Int] = null
      val a2 = boundedActor(Int.MaxValue, (i: Int) => a1 ! i - 1)
      a1 = boundedActor(Int.MaxValue,
        (i: Int) =>
          if (i == l.getCount) {
            if (i != 0) a2 ! i - 1
            l.countDown()
            l.countDown()
          }
      )
      a1 ! n
      assertCountDown(l)
    }

    "create child actor and send messages to it recursively" in {
      val l = new CountDownLatch(1)

      def a: Actor2[Int] = boundedActor(Int.MaxValue, (i: Int) => if (i > 0) a ! i - 1 else l.countDown())

      a ! (if (s eq  ActorStrategy.Sequential) 300 else n)
      assertCountDown(l)
    }

    "handle messages in order of sending by each thread" in {
      val nRounded = (n / NumOfThreads) * NumOfThreads
      val latch = new CountDownLatch(nRounded)
      val actor = countingDownBoundedActor(latch)
      for (j <- 1 to NumOfThreads) fork {
        for (i <- 1 to nRounded / NumOfThreads) {
          actor ! j -> i
        }
      }
      assertCountDown(latch)
    }

    "doesn't handle messages in simultaneous thread" in {
      val nRounded = (n / NumOfThreads) * NumOfThreads
      val l = new CountDownLatch(1)
      val a = boundedActor(Int.MaxValue, {
        var sum = 0L
        val expectedSum = nRounded * (nRounded + 1L) / 2
        (i: Int) =>
          sum += i
          if (sum == expectedSum) l.countDown()
      })
      val numOfMessagesPerThread = nRounded / NumOfThreads
      for (j <- 1 to NumOfThreads) fork {
        val off = (j - 1) * numOfMessagesPerThread
        for (i <- 1 to numOfMessagesPerThread) {
          a ! i + off
        }
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
        boundedActor(Int.MaxValue, (_: Int) => 1 / 0) ! 1
        assertCountDown(l)
      } catch {
        case e: ArithmeticException if e.getMessage == "/ by zero" =>
          l.countDown() // for a sequential strategy
          assertCountDown(l)
      } finally System.setErr(err)
    }

    "be bounded by positive number" in {
      boundedActor(0, (_: Int) => ()) must throwA[IllegalArgumentException]
    }

    "handle overflow" in {
      val i = new AtomicInteger
      val a = boundedActor(1, (_: Int) => (), onOverflow = (_: Int) => i.incrementAndGet())
      (1 to NumOfMessages).foreach(a ! _)
      if (s eq ActorStrategy.Sequential) i.get must_== 0 // a sequential strategy never overflow
      else i.get must be greaterThan 0
    }
  }

  private def countingDownUnboundedActor(l: CountDownLatch)(implicit s: ActorStrategy): Actor2[(Int, Int)] =
    unboundedActor {
      val ms = collection.mutable.Map[Int, Int]()
      (m: (Int, Int)) =>
        val (j, i) = m
        if (ms.getOrElse(j, 0) + 1 == i) {
          ms.put(j, i)
          l.countDown()
        }
    }

  private def countingDownBoundedActor(l: CountDownLatch)(implicit s: ActorStrategy): Actor2[(Int, Int)] =
    boundedActor(Int.MaxValue, {
      val ms = collection.mutable.Map[Int, Int]()
      (m: (Int, Int)) =>
        val (j, i) = m
        if (ms.getOrElse(j, 0) + 1 == i) {
          ms.put(j, i)
          l.countDown()
        }
    })

  private def assertCountDown(l: CountDownLatch, timeout: Long = 1000): Boolean =
    l.await(timeout, TimeUnit.MILLISECONDS) must_== true

  private def fork(f: => Unit): Unit = new Thread {
    override def run(): Unit = f
  }.start()
}
