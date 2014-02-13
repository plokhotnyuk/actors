package com.github.plokhotnyuk.actors

import akka.actor._
import java.util.concurrent.{ExecutorService, ThreadFactory, CountDownLatch}
import com.typesafe.config.ConfigFactory._
import com.typesafe.config.Config
import com.github.plokhotnyuk.actors.BenchmarkSpec._
import akka.dispatch.{ExecutorServiceFactory, ExecutorServiceConfigurator, DispatcherPrerequisites}

class AkkaActorSpec extends BenchmarkSpec {
  val config = load(parseString(
    """
      akka {
        log-dead-letters = 0
        log-dead-letters-during-shutdown = off
        actor {
          unstarted-push-timeout = 100s
          benchmark-dispatcher {
            executor = "com.github.plokhotnyuk.actors.CustomExecutorServiceConfigurator"
            throughput = 1024
            mailbox-type = "akka.dispatch.SingleConsumerOnlyUnboundedMailbox"
          }
        }
      }
    """))
  val actorSystem = ActorSystem("system", config)

  "Single-producer sending" in {
    val n = 12000000
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    timed(n) {
      sendMessages(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = roundToParallelism(12000000)
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    timed(n) {
      for (j <- 1 to parallelism) fork {
        sendMessages(a, n / parallelism)
      }
      l.await()
    }
  }

  "Max throughput" in {
    val n = roundToParallelism(32000000)
    val l = new CountDownLatch(parallelism)
    val as = for (j <- 1 to parallelism) yield countActor(l, n / parallelism)
    timed(n) {
      for (a <- as) fork {
        sendMessages(a, n / parallelism)
      }
      l.await()
    }
  }

  "Ping latency" in {
    ping(5000000, 1)
  }

  "Ping throughput 10K" in {
    ping(7000000, 10000)
  }

  "Initiation" in {
    val props = Props(classOf[MinimalAkkaActor]).withDispatcher("akka.actor.benchmark-dispatcher")
    footprintedAndTimedCollect(500000)(() => actorSystem.actorOf(props))
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
  }

  def shutdown(): Unit = {
    actorSystem.shutdown()
    actorSystem.awaitTermination()
  }

  private def ping(n: Int, p: Int): Unit = {
    val l = new CountDownLatch(p * 2)
    val as = (1 to p).map(_ => (replayAndCountActor(l, n / p / 2), replayAndCountActor(l, n / p / 2)))
    timed(n, printAvgLatency = p == 1) {
      as.foreach {
        case (a1, a2) => a1.tell(Message(), a2)
      }
      l.await()
    }
  }

  private def replayAndCountActor(l: CountDownLatch, n: Int): ActorRef =
    actorSystem.actorOf(Props(classOf[ReplayAndCountAkkaActor], l, n).withDispatcher("akka.actor.benchmark-dispatcher"))


  private def blockableCountActor(l1: CountDownLatch, l2: CountDownLatch, n: Int): ActorRef =
    actorSystem.actorOf(Props(classOf[BlockableCountAkkaActor], l1, l2, n).withDispatcher("akka.actor.benchmark-dispatcher"))

  private def countActor(l: CountDownLatch, n: Int): ActorRef =
    actorSystem.actorOf(Props(classOf[CountAkkaActor], l, n).withDispatcher("akka.actor.benchmark-dispatcher"))

  private def sendMessages(a: ActorRef, n: Int): Unit = {
    val m = Message()
    var i = n
    while (i > 0) {
      a ! m
      i -= 1
    }
  }
}

class ReplayAndCountAkkaActor(l: CountDownLatch, n: Int) extends Actor {
  private var i = n

  def receive = {
    case m =>
      if (i > 0) sender ! m
      i -= 1
      if (i == 0) {
        l.countDown()
        context.stop(self)
      }
  }
}

class CountAkkaActor(l: CountDownLatch, n: Int) extends Actor {
  private var i = n

  def receive = {
    case _ =>
      i -= 1
      if (i == 0) {
        l.countDown()
        context.stop(self)
      }
  }
}

class BlockableCountAkkaActor(l1: CountDownLatch, l2: CountDownLatch, n: Int) extends Actor {
  private var blocked = true
  private var i = n - 1

  def receive = {
    case _ =>
      if (blocked) {
        l1.await()
        blocked = false
      } else {
        i -= 1
        if (i == 0) {
          l2.countDown()
          context.stop(self)
        }
      }
  }
}

class MinimalAkkaActor extends Actor {
  def receive = {
    case _ =>
  }
}

class CustomExecutorServiceConfigurator(config: Config, prerequisites: DispatcherPrerequisites) extends ExecutorServiceConfigurator(config, prerequisites) {
  def createExecutorServiceFactory(id: String, threadFactory: ThreadFactory): ExecutorServiceFactory = new ExecutorServiceFactory {
    def createExecutorService: ExecutorService = BenchmarkSpec.createExecutorService()
  }
}
