package common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XY(val x: Double, val y: Double)

@Serializable
sealed class ServerMessage

@Serializable
@SerialName("login")
data class LoginMessage(val id: Int) : ServerMessage()

@Serializable
@SerialName("full_update")
data class FullRoomUpdateMessage(val roomId: String, val players: Map<Int, XY>) : ServerMessage()

@Serializable
@SerialName("update")
data class UpdateMessage(val ids: List<Int>, val xs: List<Double>, val ys: List<Double>) : ServerMessage()

@Serializable
@SerialName("add")
data class AddPlayerMessage(val id: Int, val pos: XY) : ServerMessage()

@Serializable
@SerialName("remove")
data class RemovePlayerMessage(val id: Int) : ServerMessage()

@Serializable
@SerialName("pong")
data class PongCommand(val id: Long) : ServerMessage()

@Serializable
sealed class ClientCommand

@Serializable
@SerialName("move")
data class MoveCommand(val pos: XY) : ClientCommand()

@Serializable
@SerialName("ping")
data class PingCommand(val id: Long) : ClientCommand()
