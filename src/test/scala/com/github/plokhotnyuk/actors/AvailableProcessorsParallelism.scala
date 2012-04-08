package com.github.plokhotnyuk.actors

import collection.parallel.ForkJoinTasks

trait AvailableProcessorsParallelism {
  val availableProcessors = Runtime.getRuntime.availableProcessors()

  ForkJoinTasks.defaultForkJoinPool.setParallelism(availableProcessors)
}
