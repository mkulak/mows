package common

import kotlin.math.round
import kotlin.math.sqrt

data class XY(val x: Double, val y: Double)

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


sealed class ServerMessage(val type: String)
data class LoginMessage(val id: String) : ServerMessage("login")
data class UpdateMessage(val id: String, val pos: XY) : ServerMessage("update")
data class RemovePlayerMessage(val id: String) : ServerMessage("remove")

sealed class ClientCommand
data class MoveCommand(val pos: XY) : ClientCommand()

