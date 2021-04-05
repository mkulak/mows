package common

import kotlin.math.round
import kotlin.math.sqrt


operator fun XY.plus(other: XY): XY = XY(x + other.x, y + other.y)
operator fun XY.minus(other: XY): XY = XY(x - other.x, y - other.y)
operator fun XY.times(mul: Double): XY = XY(x * mul, y * mul)
fun XY.length(): Double = sqrt(x * x + y * y)
fun XY.normalize(): XY {
    val length = length()
    return if (length != 0.0) XY(x / length, y / length) else ZERO_XY
}
fun XY.round(): XY = XY(round(x), round(y))

val ZERO_XY = XY(0.0, 0.0)
