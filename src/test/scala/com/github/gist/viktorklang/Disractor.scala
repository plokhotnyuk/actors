/**
 * Disractor = Actor + Disruptor
 *
 * Array-based non-blocking thread-safe queue implementation. It also may save old entries for EventSourcing
 *
 * Based on Viktor Klang's gist https://gist.github.com/viktorklang/2362563 
 * @author dk14 (Dmytro Kondratiuk)
 */

object Disruptor {
  import java.util.concurrent.atomic._
  trait Meta //could attach ConcurrentLinkedQueue here for parallel conflicts
  case class Entry(id: Any, value: Any, val got: Boolean = false, val meta: Option[Meta] = None)
  class Ring(size: Int, val c: ConflictResolver = new ConflictResolver {}) {
    val data = new AtomicReferenceArray[Entry](size) //we need AtomicReference in case of conflict
    var factor = new AtomicInteger(2)
    var counter = new AtomicInteger(0)
    implicit class RichAtomic(a: AtomicInteger) { def incr() = { a.compareAndSet(size - 1, 0); a.incrementAndGet}} //cyclic increment
    def allocate() = { // allocate new queue
      if (counter.compareAndSet(factor.get() - 1,0)) while(!factor.compareAndSet(factor.get(), factor.get() * 2)) {}
      counter.incrementAndGet() * (3 * size / (2* factor.get())) % size
    }
  }

  trait ConflictResolver { //resolves queues interference inside ring
    val isDrop = true //can we drop old but non-processed message
    def resolve(owner: Any, old: Entry, neww: Entry, i: Int) = { //resolve
      println(s"$owner: Dropped ${old.value} of ${old.id} (i = $i) due to no place in disruptor! Now it's ${neww.value}"); true
    } //it's also possible to throw exception, create additional linked-queue or even block processing instead of dropping here
    def processReplacedEntry(e: Entry) = null //you may do search the entry in linkedList from entry.meta
  }

  trait Queue {
    val r: Ring
    import r._; import c._
    lazy val pointer = allocate() //may be reused between actors for parallel logging etc.
    def finish(e: Entry) = true //is this actor finally processes message (like logger to cache), so it can be removed from queue
    lazy val (readPointer, writePointer) = (new AtomicInteger(pointer), new AtomicInteger(pointer)) //may be reused between actors for  load balancing
    def isEmpty = data.get(readPointer.get()) == null || readPointer.get() == writePointer.get()
    def offer(a: Any) = {
      val i = writePointer.get(); val e = data.get(i); val ne = Entry(this, a)
      if (e == null || e.got || isDrop) data.lazySet(i, ne)
      if (e != null && !e.got) resolve(this, e, ne, i)
      writePointer.incr()
    }
    def poll() = {
      val i = readPointer.get(); val datum = data.get(i)
      val r = if (datum.id == this && data.compareAndSet(i, datum, datum.copy(got = finish(datum)))) datum.value else processReplacedEntry(datum) //it's all needed in case of conflict
      readPointer.incr()
      r
    }
  }
}


object Actor {
  import Disruptor._
  import java.util.concurrent.atomic._
  import java.util.concurrent.{Executor}
  type Behavior = Any => Effect
  sealed trait Effect extends (Behavior => Behavior)
  case object Stay extends Effect { def apply(old: Behavior): Behavior = old }
  case class Become(like: Behavior) extends Effect { def apply(old: Behavior): Behavior = like }
  final val Die = Become(msg => { println("Dropping msg [" + msg + "] due to severe case of death."); Stay })  // Stay Dead plz
  trait Address { val pointer: Int; def !(msg: Any): Unit} // The notion of an Address to where you can post messages to
  private abstract class AtomicRunnableAddress extends Address with Queue with Runnable { val on = new AtomicInteger(0) }
  def apply(initial: Address => Behavior)(implicit e: Executor, ring: Ring): Address = // Seeded by the self-reference that yields the initial behavior
    new AtomicRunnableAddress { // Memory visibility of "behavior" is guarded by "on" using volatile piggybacking
      private var behavior: Behavior = { case self: Address => Become(initial(self))}
      val r = ring
      final override def !(msg: Any): Unit = behavior match { // As an optimization, we peek at our threads local copy of our behavior to see if we should bail out early
        case dead@Die.`like` => dead(msg) // Efficiently bail out if we're _known_ to be dead
        case _ => offer(msg); async() // Enqueue the message onto the mailbox and try to schedule for execution
      }
      final def run(): Unit = try { if (on.get == 1 && !isEmpty) behavior = behavior(poll())(behavior) } finally { on.set(0); async()} // Switch ourselves off, and then see if we should be rescheduled for execution
      final def async(): Unit = if (!isEmpty && on.compareAndSet(0, 1)) // If there's something to process, and we're not already scheduled
        try e.execute(this) catch { case t => on.set(0); throw t } // Schedule to run on the Executor and back out on failure
    } match { case a: Address => a ! a; a} // Make the actor self aware by seeding its address to the initial behavior
}

object ActorTest extends App {
  import Disruptor._
  import Actor._

  implicit val e: java.util.concurrent.Executor = java.util.concurrent.Executors.newCachedThreadPool
  implicit val ring = new Ring(20) //this number should be very big enough, and instance should be shared between all actors in app

  val actor = Actor( self => msg => { println("1: " + " got msg " + msg); Stay } )
  val actor2 = Actor( self => msg => { println("2: " + " got msg " + msg); Stay } )
  val actor3 = Actor( self => msg => { println("3: " + " got msg " + msg); Stay } )

  (1 to 10) foreach (x => actor !  ("A " + x))
  //Thread.sleep(1000) //this usually drops B8
  (1 to 16) foreach (x => actor2 ! ("B " + x)) //usually drops A2
  (1 to 20) foreach (x => actor3 ! ("C " + x))

}

