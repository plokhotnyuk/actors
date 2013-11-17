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
    val n = 40000000
    val l = new CountDownLatch(1)
    val a = tickActor(l, n)
    timed(n) {
      sendTicks(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = 20000000
    val l = new CountDownLatch(1)
    val a = tickActor(l, n)
    timed(n) {
      for (j <- 1 to parallelism) fork {
        sendTicks(a, n / parallelism)
      }
      l.await()
    }
  }

  "Max throughput" in {
    val n = 40000000
    val l = new CountDownLatch(parallelism)
    val as = for (j <- 1 to parallelism) yield tickActor(l, n / parallelism)
    timed(n) {
      for (a <- as) fork {
        sendTicks(a, n / parallelism)
      }
      l.await()
    }
  }

  "Ping latency" in {
    ping(10000000, 1)
  }

  "Ping throughput 1K" in {
    ping(10000000, 1000)
  }

  "Initiation 1M" in {
    val props = Props(classOf[MinimalAkkaActor]).withDispatcher("akka.actor.benchmark-dispatcher")
    footprintedCollect(1000000)(_ => actorSystem.actorOf(props))
  }

  def ping(n: Int, p: Int): Unit = {
    val l = new CountDownLatch(p * 2)
    val as = (1 to p).map(_ => (playerActor(l, n / p / 2), playerActor(l, n / p / 2)))
    timed(n) {
      as.foreach {
        case (a1, a2) => a1.tell(Message(), a2)
      }
      l.await()
    }
  }

  def shutdown(): Unit = {
    actorSystem.shutdown()
    actorSystem.awaitTermination()
  }

  private def tickActor(l: CountDownLatch, n: Int): ActorRef =
    actorSystem.actorOf(Props(classOf[TickAkkaActor], l, n).withDispatcher("akka.actor.benchmark-dispatcher"))

  private def sendTicks(a: ActorRef, n: Int): Unit = {
    val m = Message()
    var i = n
    while (i > 0) {
      a ! m
      i -= 1
    }
  }

  private def playerActor(l: CountDownLatch, n: Int): ActorRef =
    actorSystem.actorOf(Props(classOf[PlayerAkkaActor], l, n).withDispatcher("akka.actor.benchmark-dispatcher"))
}

class TickAkkaActor(l: CountDownLatch, n: Int) extends Actor {
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

class PlayerAkkaActor(l: CountDownLatch, n: Int) extends Actor {
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
