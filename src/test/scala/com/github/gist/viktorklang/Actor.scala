package com.github.gist.viktorklang

/*
   Copyright 2012 Viktor Klang

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

// Initial version from Viktor Klang: https://gist.github.com/viktorklang/2362563
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent._
import scala.annotation.tailrec

object Actor {
  type Behavior = Any => Effect

  sealed trait Effect extends (Behavior => Behavior)

  case object Stay extends Effect {
    def apply(old: Behavior): Behavior = old
  }

  case class Become(like: Behavior) extends Effect {
    def apply(old: Behavior): Behavior = like
  }

  case object Die extends Effect {
    def apply(old: Behavior): Behavior = msg => sys.error("Dropping of message due to severe case of death: " + msg)
  }

  // The notion of an Address to where you can post messages to
  trait Address {
    def !(a: Any): Unit
  }

  // Seeded by the self-reference that yields the initial behavior
  // Reduces messages asynchronously by executor to behaviour in batch loop with configurable number of iterations
  // Memory visibility of behavior is guarded by volatile piggybacking or provided by executor
  def apply(initial: Address => Behavior, batch: Int = 5)(implicit e: Executor): Address =
    new AtomicReference[AnyRef]((self: Address) => Become(initial(self))) with Address {
      // Make the actor self aware by seeding its address to the initial behavior
      this ! this

      // Enqueue the message onto the mailbox and schedule for execution if the actor was suspended
      def !(a: Any): Unit = {
        val n = new Node(a)
        getAndSet(n) match {
          case h: Node => h.lazySet(n)
          case b => asyncAct(b.asInstanceOf[Behavior], n)
        }
      }

      private def asyncAct(b: Behavior, n: Node): Unit = e match {
        case p: ForkJoinPool => p.execute(new ForkJoinTask[Unit] {
          def exec(): Boolean = {
            act(b, n, batch)
            false
          }

          def getRawResult: Unit = ()

          def setRawResult(unit: Unit): Unit = ()
        })
        case p => p.execute(new Runnable {
          def run(): Unit = act(b, n, batch)
        })
      }

      private def asyncTrySuspend(b: Behavior, n: Node): Unit = e match {
        case p: ForkJoinPool => p.execute(new ForkJoinTask[Unit] {
          def exec(): Boolean = {
            trySuspend(b, n)
            false
          }

          def getRawResult: Unit = ()

          def setRawResult(unit: Unit): Unit = ()
        })
        case p => p.execute(new Runnable {
          def run(): Unit = trySuspend(b, n)
        })
      }

      private def trySuspend(b: Behavior, n: Node): Unit =
        if (!compareAndSet(n, b)) {
          val n1 = n.get
          if (n1 eq null) asyncTrySuspend(b, n)
          else act(b, n1, batch)
        }

      @tailrec private def act(b: Behavior, n: Node, i: Int): Unit = {
        val b1 = try b(n.a).apply(b) catch {
          case t: Throwable =>
            asyncTrySuspend(b, n)
            rethrow(t)
        }
        val n1 = n.get
        if (n1 eq null) asyncTrySuspend(b1, n)
        else if (i > 0) act(b1, n1, i - 1)
        else {
          asyncAct(b1, n1)
          n.lazySet(null) // to help GC don't fall into nepotism: http://psy-lob-saw.blogspot.com/2016/03/gc-nepotism-and-linked-queues.html
        }
      }

      private def rethrow(t: Throwable): Nothing = {
        val ct = Thread.currentThread()
        if (t.isInstanceOf[InterruptedException]) ct.interrupt()
        ct.getUncaughtExceptionHandler.uncaughtException(ct, t)
        throw t
      }
    }
}

private class Node(val a: Any) extends AtomicReference[Node]
//Usage example that creates an actor that will, after it's first message is received, Die
//import Actor._
//implicit val e: java.util.concurrent.Executor = java.util.concurrent.Executors.newCachedThreadPool
//val actor = Actor(self => msg => { println("self: " + self + " got msg " + msg); Die })
//actor ! "foo"
//actor ! "foo"