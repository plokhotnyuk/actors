package com.github.plokhotnyuk.actors

import java.util.concurrent._

class ParRunner(fs: Seq[() => Unit]) {
  val barrier = new CyclicBarrier(fs.size + 1)
  fs.map(f => new Thread {
    setDaemon(true)

    override def run(): Unit = {
      barrier.await()
      f()
    }
  }).foreach(_.start())

  def start(): Unit = barrier.await()
}

abstract class JavaForkJoinTask(p: ForkJoinPool) extends ForkJoinTask[Unit] {
  p.execute(this)

  def getRawResult: Unit = ()

  def setRawResult(unit: Unit): Unit = ()
}

import scala.concurrent.forkjoin.{ForkJoinPool, ForkJoinTask}

abstract class ScalaForkJoinTask(p: ForkJoinPool) extends ForkJoinTask[Unit] {
  p.execute(this)

  def getRawResult: Unit = ()

  def setRawResult(unit: Unit): Unit = ()
}