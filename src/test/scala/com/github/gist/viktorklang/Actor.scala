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
  type Behavior[A] = A => Effect[A]
  sealed trait Effect[A] extends (Behavior[A] => Behavior[A])
  case class Stay[A]() extends Effect[A] { def apply(old: Behavior[A]): Behavior[A] = old }
  case class Become[A](like: Behavior[A]) extends Effect[A] { def apply(old: Behavior[A]): Behavior[A] = like }
  case class Die[A]() extends Effect[A] { def apply(old: Behavior[A]): Behavior[A] = (a: A) => sys.error("Dropping of message due to severe case of death: " + a) }
  trait Address[A] { def !(a: A): Unit } // The notion of an Address to where you can post messages to
  def apply[A](initial: Address[A] => Behavior[A], batch: Int = 5)(implicit e: Executor): Address[A] = // Seeded by the self-reference that yields the initial behavior
    new AtomicReference[AnyRef]((self: Address[A]) => Become(initial(self))) with Address[A] { // Memory visibility of behavior is guarded by volatile piggybacking & executor
      this ! this.asInstanceOf[A] // Make the actor self aware by seeding its address to the initial behavior
      def !(a: A): Unit = { val n = new Node(a); getAndSet(n) match { case h: Node[A] @unchecked => h.lazySet(n); case b => async(b.asInstanceOf[Behavior[A]], n, true) } } // Enqueue the message onto the mailbox and schedule for execution if the actor was suspended
      private def act(b: Behavior[A], n: Node[A]): Unit = { var b1 = b; var n1, n2 = n; var i1 = batch; try do b1 = b1(n2.a)(b1) while ({ n1 = n2; n2 = n2.get; n2 ne null } && { i1 -= 1; i1 != 0 }) finally async(b1, n1, false) } // Reduce messages to behaviour in batch loop then reschedule or suspend
      private def actOrSuspend(b: Behavior[A], n: Node[A], x: Boolean): Unit = if (x) act(b, n) else if ((get ne n) || !compareAndSet(n, b)) act(b, n.next)
      private def async(b: Behavior[A], n: Node[A], x: Boolean): Unit = e match {
        case p: scala.concurrent.forkjoin.ForkJoinPool => new scala.concurrent.forkjoin.ForkJoinTask[Unit] {
          if (p eq scala.concurrent.forkjoin.ForkJoinTask.getPool) fork() else p.execute(this)
          def exec(): Boolean = { try actOrSuspend(b, n, x) catch { case e: Throwable => rethrow(e) }; false }
          def getRawResult: Unit = ()
          def setRawResult(unit: Unit): Unit = ()
        }
        case p: ForkJoinPool => new ForkJoinTask[Unit] {
          if (p eq ForkJoinTask.getPool) fork() else p.execute(this)
          def exec(): Boolean = { try actOrSuspend(b, n, x) catch { case e: Throwable => rethrow(e) }; false }
          def getRawResult: Unit = ()
          def setRawResult(unit: Unit): Unit = ()
        }
        case p => p.execute(new Runnable { def run(): Unit = actOrSuspend(b, n, x) })
      }
      private def rethrow(e: Throwable): Unit = if (e.isInstanceOf[InterruptedException]) Thread.currentThread.interrupt() else { val t = Thread.currentThread; t.getUncaughtExceptionHandler.uncaughtException(t, e); throw e }
    }
  private class Node[A](val a: A) extends AtomicReference[Node[A]] { @tailrec final def next: Node[A] = { val n = get; if (n ne null) n else next } }
}

//Usage example that creates an actor that will, after it's first message is received, Die
//import Actor._
//implicit val e: java.util.concurrent.Executor = java.util.concurrent.Executors.newCachedThreadPool
//val actor = Actor((self: Address[String]) => (msg: String) => { println("self: " + self + " got msg " + msg); Die[String] })
//actor ! "foo"
//actor ! "foo"