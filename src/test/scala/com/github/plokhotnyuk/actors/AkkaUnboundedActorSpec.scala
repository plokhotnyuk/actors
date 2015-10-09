package com.github.plokhotnyuk.actors

import akka.actor._
import akka.dispatch.{ExecutorServiceFactory, ExecutorServiceConfigurator, DispatcherPrerequisites}
import akka.pattern.ask
import akka.util.Timeout
import com.github.plokhotnyuk.actors.BenchmarkSpec._
import com.typesafe.config.ConfigFactory._
import com.typesafe.config.Config
import java.util.concurrent.{TimeUnit, ExecutorService, ThreadFactory, CountDownLatch}
import org.specs2.execute.Success
import scala.concurrent.Await

class AkkaUnboundedActorSpec extends BenchmarkSpec {
  def config: Config = load(parseString(
    """
      akka {
        log-dead-letters = 0
        log-dead-letters-during-shutdown = off
        actor {
          unstarted-push-timeout = 100s
          benchmark-dispatcher {
            executor = "com.github.plokhotnyuk.actors.CustomExecutorServiceConfigurator"
            throughput = 1024
          }
        }
      }
    """))

  val actorSystem = ActorSystem("system", config)
  val root = actorSystem.actorOf(Props(classOf[RootAkkaActor]).withDispatcher("akka.actor.benchmark-dispatcher"))
  implicit val timeout = Timeout(1, TimeUnit.MINUTES)

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
    Await.result(root ? "Initiation", timeout.duration)
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

  def shutdown(): Unit = {
    actorSystem.terminate()
    Await.result(actorSystem.whenTerminated, timeout.duration)
  }

  private def pingLatency(n: Int): Unit =
    latencyTimed(n) {
      h =>
        val l = new CountDownLatch(2)
        val a1 = pingLatencyActor(l, n / 2, h)
        val a2 = pingLatencyActor(l, n / 2, h)
        a1.tell(Message(), a2)
        l.await()
    }

  private def pingThroughput(n: Int, p: Int): Unit = {
    val l = new CountDownLatch(p * 2)
    val as = (1 to p).map(_ => (pingThroughputActor(l, n / p / 2), pingThroughputActor(l, n / p / 2)))
    timed(n) {
      as.foreach {
        case (a1, a2) => a1.tell(Message(), a2)
      }
      l.await()
    }
  }

  private def pingLatencyActor(l: CountDownLatch, n: Int, h: LatencyHistogram): ActorRef =
    actorOf(Props(classOf[PingLatencyAkkaActor], l, n, h).withDispatcher("akka.actor.benchmark-dispatcher"))

  private def pingThroughputActor(l: CountDownLatch, n: Int): ActorRef =
    actorOf(Props(classOf[PingThroughputAkkaActor], l, n).withDispatcher("akka.actor.benchmark-dispatcher"))

  private def blockableCountActor(l1: CountDownLatch, l2: CountDownLatch, n: Int): ActorRef =
    actorOf(Props(classOf[BlockableCountAkkaActor], l1, l2, n).withDispatcher("akka.actor.benchmark-dispatcher"))

  private def countActor(l: CountDownLatch, n: Int): ActorRef =
    actorOf(Props(classOf[CountAkkaActor], l, n).withDispatcher("akka.actor.benchmark-dispatcher"))

  protected def sendMessages(a: ActorRef, n: Int): Unit = {
    val m = Message()
    var i = n
    while (i > 0) {
      a ! m
      i -= 1
    }
  }

  protected def actorOf(p: Props): ActorRef = Await.result(root ? p, timeout.duration).asInstanceOf[ActorRef]
}

private class PingLatencyAkkaActor(l: CountDownLatch, n: Int, h: LatencyHistogram) extends Actor {
  private var i = n

  def receive: Actor.Receive = {
    case m =>
      h.record()
      if (i > 0) sender ! m
      i -= 1
      if (i == 0) {
        l.countDown()
        context.stop(self)
      }
  }
}

private class PingThroughputAkkaActor(l: CountDownLatch, n: Int) extends Actor {
  private var i = n

  def receive: Actor.Receive = {
    case m =>
      if (i > 0) sender ! m
      i -= 1
      if (i == 0) {
        l.countDown()
        context.stop(self)
      }
  }
}

private class CountAkkaActor(l: CountDownLatch, n: Int) extends Actor {
  private var i = n

  def receive: Actor.Receive = {
    case _ =>
      i -= 1
      if (i == 0) {
        l.countDown()
        context.stop(self)
      }
  }
}

private class BlockableCountAkkaActor(l1: CountDownLatch, l2: CountDownLatch, n: Int) extends Actor {
  private var blocked = true
  private var i = n - 1

  def receive: Actor.Receive = {
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

private class RootAkkaActor extends Actor {
  def receive: Actor.Receive = {
    case p: Props =>
      sender ! context.actorOf(p)
    case "Initiation" =>
      footprintedAndTimedCollect(100000){
        val p = Props(classOf[MinimalAkkaActor]).withDispatcher("akka.actor.benchmark-dispatcher")
        val c = context
        () => c.actorOf(p)
      }
      sender ! "Done"
  }
}

private class MinimalAkkaActor extends Actor {
  def receive: Actor.Receive = {
    case _ =>
  }
}

private class CustomExecutorServiceConfigurator(config: Config, prerequisites: DispatcherPrerequisites) extends ExecutorServiceConfigurator(config, prerequisites) {
  def createExecutorServiceFactory(id: String, threadFactory: ThreadFactory): ExecutorServiceFactory = new ExecutorServiceFactory {
    def createExecutorService: ExecutorService = BenchmarkSpec.createExecutorService()
  }
}