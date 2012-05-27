package com.github.plokhotnyuk.actors

import sun.misc.Unsafe
import annotation.tailrec

private[actors] object Util {
  val unsafe: Unsafe = {
    val theUnsafe = classOf[Unsafe].getDeclaredField("theUnsafe")
    theUnsafe.setAccessible(true)
    theUnsafe.get(null).asInstanceOf[Unsafe]
  }
}

object PaddedAtomicLong {
  val valueOffset = Util.unsafe.arrayIndexScale(classOf[Array[Long]]) * 7 + Util.unsafe.arrayBaseOffset(classOf[Array[Long]])
}

class PaddedAtomicLong {
  private[this] val paddedValue: Array[Long] = new Array[Long](15)

  def this(initialValue: Long) {
    this()
    set(initialValue)
  }

  def get = Util.unsafe.getLongVolatile(paddedValue, PaddedAtomicLong.valueOffset)

  def lazySet(value: Long) {
    Util.unsafe.putOrderedLong(paddedValue, PaddedAtomicLong.valueOffset, value)
  }

  def set(value: Long) {
    Util.unsafe.putLongVolatile(paddedValue, PaddedAtomicLong.valueOffset, value)
  }

  def compareAndSet(expectedValue: Long, newValue: Long): Boolean =
    Util.unsafe.compareAndSwapLong(paddedValue, PaddedAtomicLong.valueOffset, expectedValue, newValue)

  override def toString: String = get.toString

  def incrementAndGet: Long = addAndGet(1L)

  @tailrec
  final def addAndGet(increment: Long): Long = {
    val currValue = get
    val newValue = currValue + increment
    if (compareAndSet(currValue, newValue)) newValue else addAndGet(increment)
  }
}

object PaddedAtomicReference {
  val valueOffset = Util.unsafe.arrayIndexScale(classOf[Array[AnyRef]]) * 7 + Util.unsafe.arrayBaseOffset(classOf[Array[AnyRef]])
}

class PaddedAtomicReference[A <: AnyRef : Manifest] {
  private[this] val paddedValue: Array[A] = new Array[A](15)

  def this(initialValue: A) {
    this()
    set(initialValue)
  }

  def get: A = Util.unsafe.getObjectVolatile(paddedValue, PaddedAtomicReference.valueOffset).asInstanceOf[A]

  def lazySet(value: A) {
    Util.unsafe.putOrderedObject(paddedValue, PaddedAtomicReference.valueOffset, value)
  }

  def set(value: A) {
    Util.unsafe.putObjectVolatile(paddedValue, PaddedAtomicReference.valueOffset, value)
  }

  def compareAndSet(expectedValue: A, newValue: A): Boolean =
    Util.unsafe.compareAndSwapObject(paddedValue, PaddedAtomicReference.valueOffset, expectedValue, newValue)

  override def toString: String = get.toString

  @tailrec
  final def getAndSet(newValue: A): A = {
    val currValue = get
    if (compareAndSet(currValue, newValue)) currValue else getAndSet(newValue)
  }
}

