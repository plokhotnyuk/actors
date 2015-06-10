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
import java.lang.Thread.{currentThread => ct}
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent._
import scala.annotation.tailrec
import scala.concurrent.forkjoin

object Actor {
  type Behavior = Any => Effect
  sealed trait Effect extends (Behavior => Behavior)
  case object Stay extends Effect { def apply(old: Behavior): Behavior = old }
  case class Become(like: Behavior) extends Effect { def apply(old: Behavior): Behavior = like }
  case object Die extends Effect { def apply(old: Behavior): Behavior = msg => sys.error("Dropping of message due to severe case of death: " + msg) }
  trait Address { def !(a: Any): Unit } // The notion of an Address to where you can post messages to
  def apply(initial: Address => Behavior, batch: Int = 5)(implicit e: Executor): Address = // Seeded by the self-reference that yields the initial behavior
    new AtomicReference[AnyRef]((self: Address) => Become(initial(self))) with Address { // Memory visibility of behavior is guarded by volatile piggybacking or provided by executor
      this ! this // Make the actor self aware by seeding its address to the initial behavior
      def !(a: Any): Unit = { val n = new Node(a); getAndSet(n) match { case h: Node => h.lazySet(n); case b => async(b.asInstanceOf[Behavior], n, true) } } // Enqueue the message onto the mailbox and schedule for execution if the actor was suspended
      private def async(b: Behavior, n: Node, x: Boolean): Unit = e match {
        case p: scala.concurrent.forkjoin.ForkJoinPool => p.execute(new scala.concurrent.forkjoin.ForkJoinTask[Unit] {
          def exec(): Boolean = { tryAct(b, n, x); false }
          def getRawResult: Unit = ()
          def setRawResult(unit: Unit): Unit = ()
        })
        case p: ForkJoinPool => p.execute(new ForkJoinTask[Unit] {
          def exec(): Boolean = { tryAct(b, n, x); false }
          def getRawResult: Unit = ()
          def setRawResult(unit: Unit): Unit = ()
        })
        case p => p.execute(new Runnable { def run(): Unit = tryAct(b, n, x) })
      }
      private def tryAct(b: Behavior, n: Node, x: Boolean): Unit = if (x) act(b, n, batch) else if ((n ne get) || !compareAndSet(n, b)) actOrAsync(b, n, 0) // Act or stop or suspend
      @tailrec private def actOrAsync(b: Behavior, n: Node, i: Int): Unit = { val n1 = n.get; if (n1 ne null) act(b, n1, batch) else if (i != 9999) actOrAsync(b, n, i + 1) else { Thread.`yield`(); async(b, n, false) } } // Spin for submit completion then act or suspend
      @tailrec private def act(b: Behavior, n: Node, i: Int): Unit = { val b1 = try b(n.a)(b) catch { case t: Throwable => asyncAndRethrow(b, n, t) }; val n1 = n.get; if ((n1 ne null) && i != 1) act(b1, n1, i - 1) else async(b1, n, false) } // Reduce messages to behaviour in batch loop then suspend
      private def asyncAndRethrow(b: Behavior, n: Node, t: Throwable): Nothing = { async(b, n, false); if (t.isInstanceOf[InterruptedException]) ct.interrupt(); uncaughtExceptionFix(t); throw t }
      private def uncaughtExceptionFix(t: Throwable): Unit = if (e.isInstanceOf[forkjoin.ForkJoinPool] || e.isInstanceOf[ForkJoinPool]) ct.getUncaughtExceptionHandler.uncaughtException(ct, t)
    }
  private class Node(val a: Any) extends AtomicReference[Node]
}

//Usage example that creates an actor that will, after it's first message is received, Die
//import Actor._
//implicit val e: java.util.concurrent.Executor = java.util.concurrent.Executors.newCachedThreadPool
//val actor = Actor(self => msg => { println("self: " + self + " got msg " + msg); Die })
//actor ! "foo"
//actor ! "foo"