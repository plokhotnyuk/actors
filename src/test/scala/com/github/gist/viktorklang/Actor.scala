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
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Executor

object Actor {
  type Behavior[A] = A => Effect[A]
  sealed trait Effect[A] extends (Behavior[A] => Behavior[A])
  case class Stay[A]() extends Effect[A] { def apply(old: Behavior[A]): Behavior[A] = old }
  case class Become[A](like: Behavior[A]) extends Effect[A] { def apply(old: Behavior[A]): Behavior[A] = like }
  case class Die[A]() extends Effect[A] { def apply(old: Behavior[A]): Behavior[A] = (a: A) => sys.error("Dropping of message due to severe case of death: " + a) }
  trait Address[A] { def !(a: A): Unit } // The notion of an Address to where you can post messages to
  def apply[A](initial: Behavior[A], batch: Int = 5)(implicit e: Executor): Address[A] = // Seeded by the self-reference that yields the initial behavior
    new AtomicReference[AnyRef](initial) with Address[A] { // Memory visibility of behavior is guarded by volatile piggybacking & executor
      def !(a: A): Unit = { val n = new Node(a); getAndSet(n) match { case h: Node[A] @unchecked => h.lazySet(n); case b: Behavior[A] @unchecked => async(b, n) } } // Enqueue the message onto the mailbox or schedule for execution
      private def async(b: Behavior[A], n: Node[A]): Unit = e.execute(new Runnable { def run(): Unit = act(b, n) })
      private def act(b: Behavior[A], n: Node[A]): Unit = { var b1 = b; var n1, n2 = n; var i = batch; try do { n1 = n2; b1 = b1(n1.a)(b1); n2 = n1.get; i -= 1 } while ((n2 ne null) && i != 0) finally lastTry(b1, n1) } // Reduce messages to behaviour in batch loop
      private def lastTry(b: Behavior[A], n: Node[A]): Unit = e.execute(new Runnable { def run(): Unit = if ((n ne get) || !compareAndSet(n, b)) act(b, n.next) }) // Schedule to last try for execution or switch ourselves off
    }
}

private final class Node[A](val a: A) extends AtomicReference[Node[A]] {
  @annotation.tailrec def next: Node[A] = { val n = get; if (n ne null) n else next }
}

//Usage example that creates an actor that will, after it's first message is received, Die
//import Actor._
//implicit val e: java.util.concurrent.Executor = java.util.concurrent.Executors.newCachedThreadPool
//val actor = Actor((msg: String) => { println("self: " + self + " got msg " + msg); Die[String]() })
//actor ! "foo"
//actor ! "foo"