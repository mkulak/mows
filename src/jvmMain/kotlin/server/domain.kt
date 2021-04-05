package server

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import common.XY
import io.vertx.core.http.ServerWebSocket

data class ConnectedClient(
    val id: PlayerId,
    val ws: ServerWebSocket
)

data class PlayerId @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(@JsonValue val value: String) {
    override fun toString() = value
}

data class Player(
    val id: PlayerId,
    val pos: XY
)

