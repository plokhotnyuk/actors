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
  final val Die = Become(msg => sys.error("Dropping of message due to severe case of death: " + msg))
  trait Address { def !(msg: Any): Unit } // The notion of an Address to where you can post messages to
  def apply(initial: Address => Behavior, batch: Int = 5)(implicit e: Executor): Address = // Seeded by the self-reference that yields the initial behavior
    new AtomicReference[Node] with Address { // Memory visibility of behavior is guarded by volatile piggybacking
      @volatile private var behavior: Behavior = { case self: Address => Become(initial(self)) } // Rebindable top of the mailbox, bootstrapped to identity
      this ! this // Make the actor self aware by seeding its address to the initial behavior
      def !(msg: Any): Unit = { val n = new Node(msg); val h = getAndSet(n); if (h ne null) h.lazySet(n) else schedule(n) } // Enqueue the message onto the mailbox and try to schedule for execution
      private def schedule(t: Node): Unit = e.execute(new Runnable { def run(): Unit = act(t) })
      private def act(t: Node): Unit = { var n, n2 = t; var b = behavior; var i = batch; try do { n = n2; b = b(n.msg)(b); n2 = n.get; i -= 1 } while ((n2 ne null) && i != 0) finally { behavior = b; scheduleOrSuspend(n) } } // Switch ourselves off in batch loop, and then see if we should be rescheduled for execution
      private def scheduleOrSuspend(t: Node): Unit = e.execute(new Runnable { def run(): Unit = if ((t ne get) || !compareAndSet(t, null)) act(t.next)})
    }
}

private final class Node(val msg: Any) extends AtomicReference[Node] {
  @annotation.tailrec def next: Node = { val n = get; if (n ne null) n else next }
}

//Usage

//import Actor._

//implicit val e: java.util.concurrent.Executor = java.util.concurrent.Executors.newCachedThreadPool

//Creates an actor that will, after it's first message is received, Die
//val actor = Actor( self => msg => { println("self: " + self + " got msg " + msg); Die } )

//actor ! "foo"
//actor ! "foo"