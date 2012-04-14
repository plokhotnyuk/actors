package com.github.plokhotnyuk.actors

trait BackOff {
  protected def backOffThreshold = 1024
  private[this] var spin = backOffThreshold

  def backOff() {
    spin -= 1
    if (spin == 0) {
      spin = backOffThreshold
      Thread.`yield`()
    }
  }
}
