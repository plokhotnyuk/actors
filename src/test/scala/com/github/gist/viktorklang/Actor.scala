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
  case object Stay extends Effect { def apply(old: Behavior): Behavior = old }
  case class Become(like: Behavior) extends Effect { def apply(old: Behavior): Behavior = like }
  case object Die extends Effect { def apply(old: Behavior): Behavior = msg => sys.error("Dropping of message due to severe case of death: " + msg) }
  trait Address { def !(msg: Any): Unit } // The notion of an Address to where you can post messages to
  def apply(initial: Address => Behavior, batch: Int = 5)(implicit e: Executor): Address = // Seeded by the self-reference that yields the initial behavior
    new AtomicReference[AnyRef]((self: Any) => Become(initial(self.asInstanceOf[Address]))) with Address { // Memory visibility of behavior is guarded by volatile piggybacking & executor
      this ! this // Make the actor self aware by seeding its address to the initial behavior
      def !(msg: Any): Unit = { val n = new Node(msg); getAndSet(n) match { case h: Node => h.lazySet(n); case b => async(b.asInstanceOf[Behavior], n) } } // Enqueue the message onto the mailbox and schedule for execution if the actor was suspended
      @tailrec private def act(b: Behavior, n: Node, i: Int = batch): Unit = { val b1 = b(n.msg)(b); val n1 = n.get; if ((n1 ne null) && i != 0) act(b1, n1, i - 1) else if (n1 ne null) async(b1, n1) else if (!compareAndSet(n, b1)) async(b1, n.next) } // Reduce messages to behaviour in batch loop then reschedule or suspend
      private def async(b: Behavior, n: Node): Unit = e match { // Schedule for execution
        case p: scala.concurrent.forkjoin.ForkJoinPool => new scala.concurrent.forkjoin.ForkJoinTask[Unit] {
          if (p eq scala.concurrent.forkjoin.ForkJoinTask.getPool) fork() else p.execute(this)
          def exec(): Boolean = { act(b, n); false }
          def getRawResult: Unit = ()
          def setRawResult(unit: Unit): Unit = ()
        }
        case p: ForkJoinPool => new ForkJoinTask[Unit] {
          if (p eq ForkJoinTask.getPool) fork() else p.execute(this)
          def exec(): Boolean = { act(b, n); false }
          def getRawResult: Unit = ()
          def setRawResult(unit: Unit): Unit = ()
        }
        case p => p.execute(new Runnable { def run(): Unit = act(b, n) })
      }
    }
  private class Node(val msg: Any) extends AtomicReference[Node] { @tailrec final def next: Node = { val n = get; if (n ne null) n else next } }
}

//Usage example that creates an actor that will, after it's first message is received, Die
//import Actor._
//implicit val e: java.util.concurrent.Executor = java.util.concurrent.Executors.newCachedThreadPool
//val actor = Actor(self => msg => { println("self: " + self + " got msg " + msg); Die })
//actor ! "foo"
//actor ! "foo"