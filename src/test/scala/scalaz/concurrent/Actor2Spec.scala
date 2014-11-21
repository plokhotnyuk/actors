package scalaz.concurrent

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger
import org.specs2.mutable.Specification

import scala.concurrent.forkjoin

class Actor2Spec extends Specification {
  args(sequential = true)

  val NumOfMessages = 10000
  val NumOfThreads = 4

  "actor with actor strategy backed by Scala fork-join pool" should {
    implicit val s: ActorStrategy = ActorStrategy.Executor(new forkjoin.ForkJoinPool())
    unboundedActorTests(NumOfMessages)
    boundedActorTests(NumOfMessages)
  }

  "actor with actor strategy backed by Java fork-join pool" should {
    implicit val s: ActorStrategy = ActorStrategy.Executor(new ForkJoinPool())
    unboundedActorTests(NumOfMessages)
    boundedActorTests(NumOfMessages)
  }

  "actor with actor strategy backed by fixed thread pool" should {
    implicit val s: ActorStrategy = ActorStrategy.Executor(Strategy.DefaultExecutorService)
    unboundedActorTests(NumOfMessages)
    boundedActorTests(NumOfMessages)
  }

  def unboundedActorTests(n: Int)(implicit s: ActorStrategy) = {
    "execute code async" in {
      val latch = new CountDownLatch(1)
      val actor = Actor2.unboundedActor[Int]((i: Int) => latch.countDown())
      actor ! 1
      assertCountDown(latch)
    }

    "catch code errors that can be handled" in {
      val latch = new CountDownLatch(1)
      val actor = Actor2.unboundedActor[Int]((i: Int) => 1 / 0, (ex: Throwable) => latch.countDown())
      actor ! 1
      assertCountDown(latch)
    }

    "exchange messages with another actor without loss" in {
      val latch = new CountDownLatch(n)
      var actor1: Actor2[Int] = null
      val actor2 = Actor2.unboundedActor[Int]((i: Int) => actor1 ! i - 1)
      actor1 = Actor2.unboundedActor[Int] {
        (i: Int) =>
          if (i == latch.getCount) {
            if (i != 0) actor2 ! i - 1
            latch.countDown()
            latch.countDown()
          }
      }
      actor1 ! n
      assertCountDown(latch)
    }

    "send messages to itself and process them" in {
      val latch = new CountDownLatch(1)
      var actor: Actor2[Int] = null
      actor = Actor2.unboundedActor[Int] {
        (i: Int) =>
          if (i > 0) actor ! i - 1
          else latch.countDown()
      }
      actor ! n
      assertCountDown(latch)
    }

    "handle messages in order of sending by each thread" in {
      val nRounded = (n / NumOfThreads) * NumOfThreads
      val latch = new CountDownLatch(nRounded)
      val actor = countingDownUnboundedActor(latch)
      for (j <- 1 to NumOfThreads) fork {
        for (i <- 1 to nRounded / NumOfThreads) {
          actor !(j, i)
        }
      }
      assertCountDown(latch)
    }

    "redirect unhandled errors to uncaught exception handler of thread" in {
      val l = new CountDownLatch(1)
      val err = System.err
      try {
        System.setErr(new java.io.PrintStream(new java.io.OutputStream {
          override def write(b: Int): Unit = l.countDown()
        }))
        Actor2.unboundedActor((_: Int) => 1 / 0) ! 1
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
      val latch = new CountDownLatch(1)
      val actor = Actor2.boundedActor[Int](Int.MaxValue, (i: Int) => latch.countDown())
      actor ! 1
      assertCountDown(latch)
    }

    "catch code errors that can be handled" in {
      val latch = new CountDownLatch(1)
      val actor = Actor2.boundedActor[Int](Int.MaxValue, (i: Int) => 1 / 0, (ex: Throwable) => latch.countDown())
      actor ! 1
      assertCountDown(latch)
    }

    "exchange messages with another actor without loss" in {
      val latch = new CountDownLatch(n)
      var actor1: Actor2[Int] = null
      val actor2 = Actor2.boundedActor[Int](Int.MaxValue, (i: Int) => actor1 ! i - 1)
      actor1 = Actor2.boundedActor[Int](Int.MaxValue,
        (i: Int) =>
          if (i == latch.getCount) {
            if (i != 0) actor2 ! i - 1
            latch.countDown()
            latch.countDown()
          }
      )
      actor1 ! n
      assertCountDown(latch)
    }

    "send messages to itself and process them" in {
      val latch = new CountDownLatch(1)
      var actor: Actor2[Int] = null
      actor = Actor2.boundedActor[Int](Int.MaxValue,
        (i: Int) =>
          if (i > 0) actor ! i - 1
          else latch.countDown()
      )
      actor ! n
      assertCountDown(latch)
    }

    "handle messages in order of sending by each thread" in {
      val nRounded = (n / NumOfThreads) * NumOfThreads
      val latch = new CountDownLatch(nRounded)
      val actor = countingDownBoundedActor(latch)
      for (j <- 1 to NumOfThreads) fork {
        for (i <- 1 to nRounded / NumOfThreads) {
          actor !(j, i)
        }
      }
      assertCountDown(latch)
    }

    "redirect unhandled errors to uncaught exception handler of thread" in {
      val l = new CountDownLatch(1)
      val err = System.err
      try {
        System.setErr(new java.io.PrintStream(new java.io.OutputStream {
          override def write(b: Int): Unit = l.countDown()
        }))
        Actor2.boundedActor(Int.MaxValue, (_: Int) => 1 / 0) ! 1
        assertCountDown(l)
      } catch {
        case e: ArithmeticException if e.getMessage == "/ by zero" =>
          l.countDown() // for a sequential strategy
          assertCountDown(l)
      } finally System.setErr(err)
    }

    "be bounded by positive number" in {
      Actor2.boundedActor(0, (_: Int) => ()) must throwA[IllegalArgumentException]
    }

    "handle overflow" in {
      val i = new AtomicInteger
      val a = Actor2.boundedActor(1, (_: Int) => (), onOverflow = (_: Int) => i.incrementAndGet())
      (1 to NumOfMessages).foreach(a ! _)
      i.get must be greaterThan 0
    }
  }

  def countingDownUnboundedActor(latch: CountDownLatch)(implicit s: ActorStrategy): Actor2[(Int, Int)] =
    Actor2.unboundedActor[(Int, Int)] {
      val ms = collection.mutable.Map[Int, Int]()

      (m: (Int, Int)) =>
        val (j, i) = m
        if (ms.getOrElse(j, 0) + 1 == i) {
          ms.put(j, i)
          latch.countDown()
        }
    }

  private def countingDownBoundedActor(l: CountDownLatch)(implicit s: ActorStrategy): Actor2[(Int, Int)] =
    Actor2.boundedActor(Int.MaxValue, {
      val ms = collection.mutable.Map[Int, Int]()

      (m: (Int, Int)) =>
        val (j, i) = m
        if (ms.getOrElse(j, 0) + 1 == i) {
          ms.put(j, i)
          l.countDown()
        }
    })

  private def assertCountDown(latch: CountDownLatch, timeout: Long = 1000): Boolean =
    latch.await(timeout, TimeUnit.MILLISECONDS) must_== true

  private def fork(f: => Unit): Unit = new Thread {
    override def run(): Unit = f
  }.start()
}
