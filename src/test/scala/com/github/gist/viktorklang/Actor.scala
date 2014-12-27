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

object Actor {
  import java.util.concurrent.{ConcurrentLinkedQueue, Executor}
  import java.util.concurrent.atomic.{AtomicInteger}
  type Behavior = Any => Effect
  sealed trait Effect extends (Behavior => Behavior)
  case object Stay extends Effect { def apply(old: Behavior): Behavior = old }
  case class Become(like: Behavior) extends Effect { def apply(old: Behavior): Behavior = like }
  final val Die = Become(msg => { println("Dropping msg [" + msg + "] due to severe case of death."); Stay }) // Stay Dead plz
  trait Address { def !(msg: Any): Unit } // The notion of an Address to where you can post messages to
  private abstract class AtomicRunnableAddress extends Address with Runnable { val on = new AtomicInteger(0) }
  def apply(initial: Address => Behavior)(implicit e: Executor): Address =              // Seeded by the self-reference that yields the initial behavior
    new AtomicRunnableAddress { // Memory visibility of "behavior" is guarded by "on" using volatile piggybacking
    private final val mbox = new ConcurrentLinkedQueue[Any]                           // Our awesome little mailbox, free of blocking and evil
    private var behavior: Behavior = { case self: Address => Become(initial(self)) } // Rebindable top of the mailbox, bootstrapped to identity
    final override def !(msg: Any): Unit = behavior match { // As an optimization, we peek at our threads local copy of our behavior to see if we should bail out early
        case dead @ Die.`like` => dead(msg)                   // Efficiently bail out if we're _known_ to be dead
        case _ => mbox.offer(msg); async()      // Enqueue the message onto the mailbox and try to schedule for execution
      }
      final def run(): Unit = try { val msg = mbox.poll(); if (msg != null) behavior = behavior(msg)(behavior) } finally { on.set(0); async() } // Switch ourselves off, and then see if we should be rescheduled for execution
      final def async(): Unit = if(!mbox.isEmpty && on.compareAndSet(0, 1))         // If there's something to process, and we're not already scheduled
          try e.execute(this) catch { case t => on.set(0); throw t } // Schedule to run on the Executor and back out on failure
    } match { case a: Address => a ! a; a } // Make the actor self aware by seeding its address to the initial behavior
}

//Usage

//import Actor._

//implicit val e: java.util.concurrent.Executor = java.util.concurrent.Executors.newCachedThreadPool

//Creates an actor that will, after it's first message is received, Die
//val actor = Actor( self => msg => { println("self: " + self + " got msg " + msg); Die } )

//actor ! "foo"
//actor ! "foo"