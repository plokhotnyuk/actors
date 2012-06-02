package com.github.plokhotnyuk.actors

import sun.misc.Unsafe
import annotation.tailrec

object Util {
  val unsafe = {
    val theUnsafe = classOf[Unsafe].getDeclaredField("theUnsafe")
    theUnsafe.setAccessible(true)
    theUnsafe.get(null).asInstanceOf[Unsafe]
  }

  val paddingSize = 128

  def getArraySize(arrayClass: Class[_]): Int =
    (paddingSize - unsafe.arrayBaseOffset(arrayClass)) / unsafe.arrayIndexScale(arrayClass)

  def getValueOffset(arrayClass: Class[_]): Int = (paddingSize >> 1) - unsafe.arrayIndexScale(arrayClass)
}

import Util._
import Util.unsafe._

object PaddedAtomicInt {
  val valueOffset = getValueOffset(classOf[Array[Int]])
  val arraySize = getArraySize(classOf[Array[Int]])
}

class PaddedAtomicInt {
  import PaddedAtomicInt._

  private[this] val paddedValue = new Array[Int](arraySize)

  def this(initialValue: Int) {
    this()
    set(initialValue)
  }

  def get = getIntVolatile(paddedValue, valueOffset)

  def lazySet(value: Int) {
    putOrderedInt(paddedValue, valueOffset, value)
  }

  def set(value: Int) {
    putIntVolatile(paddedValue, valueOffset, value)
  }

  def compareAndSet(expectedValue: Int, newValue: Int): Boolean =
    compareAndSwapLong(paddedValue, valueOffset, expectedValue, newValue)

  def incrementAndGet: Int = addAndGet(1)

  @tailrec
  final def addAndGet(increment: Int): Int = {
    val currValue = get
    val newValue = currValue + increment
    if (compareAndSet(currValue, newValue)) newValue else addAndGet(increment)
  }

  override def toString: String = get.toString
}

object PaddedAtomicLong {
  val valueOffset = getValueOffset(classOf[Array[Long]])
  val arraySize = getArraySize(classOf[Array[Long]])
}

class PaddedAtomicLong {
  import PaddedAtomicLong._

  private[this] val paddedValue = new Array[Long](arraySize)

  def this(initialValue: Long) {
    this()
    set(initialValue)
  }

  def get = getLongVolatile(paddedValue, valueOffset)

  def lazySet(value: Long) {
    putOrderedLong(paddedValue, valueOffset, value)
  }

  def set(value: Long) {
    putLongVolatile(paddedValue, valueOffset, value)
  }

  def compareAndSet(expectedValue: Long, newValue: Long): Boolean =
    compareAndSwapLong(paddedValue, valueOffset, expectedValue, newValue)

  def incrementAndGet: Long = addAndGet(1L)

  @tailrec
  final def addAndGet(increment: Long): Long = {
    val currValue = get
    val newValue = currValue + increment
    if (compareAndSet(currValue, newValue)) newValue else addAndGet(increment)
  }

  override def toString: String = get.toString
}

object PaddedAtomicReference {
  val valueOffset = getValueOffset(classOf[Array[AnyRef]])
  val arraySize = getArraySize(classOf[Array[AnyRef]])
}

class PaddedAtomicReference[A <: AnyRef : Manifest] {
  import PaddedAtomicReference._

  private[this] val paddedValue = new Array[A](arraySize)

  def this(initialValue: A) {
    this()
    set(initialValue)
  }

  def get: A = getObjectVolatile(paddedValue, valueOffset).asInstanceOf[A]

  def lazySet(value: A) {
    putOrderedObject(paddedValue, valueOffset, value)
  }

  def set(value: A) {
    putObjectVolatile(paddedValue, valueOffset, value)
  }

  def compareAndSet(expectedValue: A, newValue: A): Boolean =
    compareAndSwapObject(paddedValue, valueOffset, expectedValue, newValue)

  @tailrec
  final def getAndSet(newValue: A): A = {
    val currValue = get
    if (compareAndSet(currValue, newValue)) currValue else getAndSet(newValue)
  }

  override def toString: String = get.toString
}
