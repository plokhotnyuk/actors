package com.github.plokhotnyuk.actors

import java.util.concurrent.atomic.AtomicReference

private[actors] class Node[A](val a: A) extends AtomicReference[Node[A]]