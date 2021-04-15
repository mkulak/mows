package server

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import common.XY
import io.vertx.core.http.ServerWebSocket

data class ConnectedClient(
    val id: PlayerId,
    val ws: ServerWebSocket
)

data class PlayerId @JsonCreator(mode = DELEGATING) constructor(@JsonValue val value: String) {
    override fun toString() = value
}

data class RoomId @JsonCreator(mode = DELEGATING) constructor(@JsonValue val value: String) {
    override fun toString() = value
}

data class Player(
    val id: PlayerId,
    val roomId: RoomId,
    val pos: XY
)

data class Room(
    val id: RoomId,
    val playerIds: MutableSet<PlayerId>,
    val playersWithUpdates: MutableSet<PlayerId>,
    var lastUpdateSentAt: Long
)
