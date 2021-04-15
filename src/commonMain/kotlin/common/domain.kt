package common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XY(val x: Double, val y: Double)

@Serializable
sealed class ServerMessage

@Serializable
@SerialName("login")
data class LoginMessage(val id: String) : ServerMessage()

@Serializable
@SerialName("full_update")
data class FullRoomUpdateMessage(val roomId: String, val players: Map<String, XY>) : ServerMessage()

@Serializable
@SerialName("update")
data class UpdateMessage(val ids: List<String>, val xs: List<Double>, val ys: List<Double>) : ServerMessage()

@Serializable
@SerialName("add")
data class AddPlayerMessage(val id: String, val pos: XY) : ServerMessage()

@Serializable
@SerialName("remove")
data class RemovePlayerMessage(val id: String) : ServerMessage()

@Serializable
sealed class ClientCommand

@Serializable
@SerialName("move")
data class MoveCommand(val pos: XY) : ClientCommand()

