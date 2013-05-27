package com.github.plokhotnyuk.actors;

import akka.util.Unsafe;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock-free MPSC linked queue implementation based on Dmitriy Vyukov's non-intrusive MPSC queue:
 * http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue
 */
public abstract class PatchedAbstractNodeQueue<T> extends AtomicReference<PatchedAbstractNodeQueue.Node<T>> {
    // Extends AtomicReference for the "head" slot (which is the one that is appended to) since Unsafe does not expose XCHG operation intrinsically
    private volatile Node<T> _tailDoNotCallMeDirectly;
    private volatile long dummy;

    protected PatchedAbstractNodeQueue() {
        final Node<T> n = new Node<T>();
        _tailDoNotCallMeDirectly = n;
        set(n);
    }

    @SuppressWarnings("unchecked")
    protected final Node<T> peekNode() {
        return ((Node<T>)Unsafe.instance.getObjectVolatile(this, tailOffset)).next();
    }

    public final T peek() {
        final Node<T> n = peekNode();
        return (n != null) ? n.value : null;
    }

    public final void add(final T value) {
        final Node<T> n = new Node<T>(value);
        getAndSet(n).setNext(n);
    }

    public final boolean isEmpty() {
        boolean empty = peekNode() == null;
        if (empty) {
            dummy = 1;
            return peekNode() == null;
        } else {
            return false;
        }
    }

    public final int count() {
        int count = 0;
        for(Node<T> n = peekNode();n != null; n = n.next())
            ++count;
        return count;
    }

    @SuppressWarnings("unchecked")
    public final T poll() {
        final Node<T> next = peekNode();
        if (next == null) return null;
        else {
            final T ret = next.value;
            next.value = null; // Null out the value so that we can GC it early
            Unsafe.instance.putOrderedObject(this, tailOffset, next);
            return ret;
        }
    }

    private final static long tailOffset;

    static {
        try {
            tailOffset = Unsafe.instance.objectFieldOffset(PatchedAbstractNodeQueue.class.getDeclaredField("_tailDoNotCallMeDirectly"));
        } catch(Throwable t){
            throw new ExceptionInInitializerError(t);
        }
    }

    public static class Node<T> {
        T value;
        private volatile Node<T> _nextDoNotCallMeDirectly;

        Node() {
            this(null);
        }

        Node(final T value) {
            this.value = value;
        }

        @SuppressWarnings("unchecked")
        public final Node<T> next() {
            return (Node<T>)Unsafe.instance.getObjectVolatile(this, nextOffset);
        }

        protected final void setNext(final Node<T> newNext) {
            Unsafe.instance.putOrderedObject(this, nextOffset, newNext);
        }

        private final static long nextOffset;

        static {
            try {
                nextOffset = Unsafe.instance.objectFieldOffset(Node.class.getDeclaredField("_nextDoNotCallMeDirectly"));
            } catch(Throwable t){
                throw new ExceptionInInitializerError(t);
            }
        }
    }
}

