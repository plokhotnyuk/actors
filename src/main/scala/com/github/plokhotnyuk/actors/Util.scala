package com.github.plokhotnyuk.actors

import sun.misc.Unsafe
import annotation.tailrec

object Util {
  val unsafe: Unsafe = {
    val theUnsafe = classOf[Unsafe].getDeclaredField("theUnsafe")
    theUnsafe.setAccessible(true)
    theUnsafe.get(null).asInstanceOf[Unsafe]
  }
}

import Util._

object PaddedAtomicLong {
  val valueOffset = unsafe.arrayIndexScale(classOf[Array[Long]]) * 7 + unsafe.arrayBaseOffset(classOf[Array[Long]])
}

class PaddedAtomicLong {
  import PaddedAtomicLong._

  private[this] val paddedValue = new Array[Long](15)

  def this(initialValue: Long) {
    this()
    set(initialValue)
  }

  def get = unsafe.getLongVolatile(paddedValue, valueOffset)

  def lazySet(value: Long) {
    unsafe.putOrderedLong(paddedValue, valueOffset, value)
  }

  def set(value: Long) {
    unsafe.putLongVolatile(paddedValue, valueOffset, value)
  }

  def compareAndSet(expectedValue: Long, newValue: Long): Boolean =
    unsafe.compareAndSwapLong(paddedValue, valueOffset, expectedValue, newValue)

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
  val valueOffset = unsafe.arrayIndexScale(classOf[Array[AnyRef]]) * 7 + unsafe.arrayBaseOffset(classOf[Array[AnyRef]])
}

class PaddedAtomicReference[A <: AnyRef : Manifest] {
  import PaddedAtomicReference._

  private[this] val paddedValue = new Array[A](15)

  def this(initialValue: A) {
    this()
    set(initialValue)
  }

  def get: A = unsafe.getObjectVolatile(paddedValue, valueOffset).asInstanceOf[A]

  def lazySet(value: A) {
    unsafe.putOrderedObject(paddedValue, valueOffset, value)
  }

  def set(value: A) {
    unsafe.putObjectVolatile(paddedValue, valueOffset, value)
  }

  def compareAndSet(expectedValue: A, newValue: A): Boolean =
    unsafe.compareAndSwapObject(paddedValue, valueOffset, expectedValue, newValue)

  override def toString: String = get.toString

  @tailrec
  final def getAndSet(newValue: A): A = {
    val currValue = get
    if (compareAndSet(currValue, newValue)) currValue else getAndSet(newValue)
  }
}

