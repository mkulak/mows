import kotlin.math.sqrt

data class XY(val x: Double, val y: Double)

operator fun XY.plus(other: XY): XY = XY(x + other.x, y + other.y)
operator fun XY.minus(other: XY): XY = XY(x - other.x, y - other.y)
operator fun XY.times(mul: Double): XY = XY(x * mul, y * mul)
fun XY.normalize(): XY = length().let { XY(x / it, y / it) }
fun XY.length(): Double = sqrt(x * x + y * y)


sealed class ServerMessage(val type: String)
data class LoginMessage(val id: String) : ServerMessage("login")
data class UpdateMessage(val id: String, val pos: XY) : ServerMessage("update")

sealed class ClientCommand
data class MoveCommand(val pos: XY) : ClientCommand()

