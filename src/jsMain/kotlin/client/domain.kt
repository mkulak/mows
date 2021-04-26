package client

import common.XY

data class Player(
    val id: PlayerId,
    var serverPos: XY,
    var pos: XY,
)

data class Room(
    var id: RoomId,
    var myId: PlayerId,
    val players: MutableMap<PlayerId, Player>
) {
    fun clear() {
        id = RoomId("")
        myId = PlayerId(0)
        players.clear()
    }
    fun me(): Player? = players[myId]
}

data class PlayerId(val value: Int) {
    override fun toString() = value.toString()
}

data class RoomId(val value: String) {
    override fun toString() = value
}
