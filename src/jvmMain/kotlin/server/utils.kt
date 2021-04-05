package server

import kotlin.random.Random

fun nextId(): String = Integer.toHexString(Random.nextInt()).toString()
