package com.github.plokhotnyuk.actors

import collection.parallel.ForkJoinTasks

trait AvailableProcessorsParallelism {
  ForkJoinTasks.defaultForkJoinPool.setParallelism(Runtime.getRuntime.availableProcessors())
}
