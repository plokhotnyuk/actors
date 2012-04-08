package com.github.plokhotnyuk.actors

import scalaz.concurrent.Actor

case class Ball(hitCountdown: Int)

case class BallZ(hitCountdown: Int, player1: Actor[BallZ], player2: Actor[BallZ])

case class Data()

case class Message()

case class Tick()

