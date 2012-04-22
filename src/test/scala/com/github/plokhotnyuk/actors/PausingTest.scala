package com.github.plokhotnyuk.actors

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import com.github.plokhotnyuk.actors.Helper._
import java.util.concurrent.locks.LockSupport

@RunWith(classOf[JUnitRunner])
class PausingTest extends Specification {

  "Thread.`yield`()" in {
    val n = 10000000
    timed("Thread.`yield`()", n) {
      var i = n
      while (i > 0) {
        Thread.`yield`()
        i -= 1
      }
    }
  }

  "Thread.sleep(0)" in {
    val n = 10000000
    timed("Thread.sleep(0)", n) {
      var i = n
      while (i > 0) {
        Thread.sleep(0)
        i -= 1
      }
    }
  }

  "lock.wait(0, 1)" in {
    val n = 1000
    timed("lock.wait(0, 1)", n) {
      var i = n
      val lock = new AnyRef()
      lock.synchronized {
        while (i > 0) {
          lock.wait(0, 1)
          i -= 1
        }
      }
    }
  }

  "LockSupport.parkNanos(1)" in {
    val n = 1000
    timed("LockSupport.parkNanos(1)", n) {
      var i = n
      while (i > 0) {
        LockSupport.parkNanos(1)
        i -= 1
      }
    }
  }
}