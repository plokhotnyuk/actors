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
  type Behavior = Any => Effect
  sealed trait Effect extends (Behavior => Behavior)
  case object Stay extends Effect { def apply(old: Behavior): Behavior = old }
  case class Become(like: Behavior) extends Effect { def apply(old: Behavior): Behavior = like }
  final val Die = Become(msg => { println("Dropping msg [" + msg + "] due to severe case of death."); Stay }) // Stay Dead plz
  trait Address { def !(msg: Any): Unit } // The notion of an Address to where you can post messages to
  def apply(initial: Address => Behavior)(implicit e: Executor): Address = // Seeded by the self-reference that yields the initial behavior
    new AtomicReference[Node] with Address { // Memory visibility of "behavior" is guarded by "on" using volatile piggybacking
      private var behavior: Behavior = { case self: Address => Become(initial(self)) } // Rebindable top of the mailbox, bootstrapped to identity
      final def !(msg: Any): Unit = { val n = new Node(msg); val h = getAndSet(n); if (h ne null) h.lazySet(n) else schedule(n) } // Enqueue the message onto the mailbox and try to schedule for execution
      private def schedule(n: Node): Unit = e.execute(new Runnable { def run(): Unit = act(n) })
      private def act(n: Node): Unit = try behavior = behavior(n.msg)(behavior) finally scheduleOrSuspend(n) // Switch ourselves off, and then see if we should be rescheduled for execution
      private def scheduleOrSuspend(n: Node): Unit = e.execute(new Runnable { def run(): Unit = if ((n ne get) || !compareAndSet(n, null)) act(n.next)})
    } match { case a: Address => a ! a; a } // Make the actor self aware by seeding its address to the initial behavior
}

private final class Node(val msg: Any) extends AtomicReference[Node] {
  @annotation.tailrec def next: Node = { val n2 = get; if (n2 ne null) n2 else next }
}

//Usage

//import Actor._

//implicit val e: java.util.concurrent.Executor = java.util.concurrent.Executors.newCachedThreadPool

//Creates an actor that will, after it's first message is received, Die
//val actor = Actor( self => msg => { println("self: " + self + " got msg " + msg); Die } )

//actor ! "foo"
//actor ! "foo"