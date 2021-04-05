package client

import common.LoginMessage
import common.MoveCommand
import common.RemovePlayerMessage
import common.UpdateMessage
import common.XY
import common.ZERO_XY
import common.plus
import common.round

class GameLogic {
    lateinit var networkModule: NetworkModule
    val room = Room(RoomId(""), PlayerId(""), HashMap())

    fun handle(message: LoginMessage) {
        room.myId = PlayerId(message.id)
    }

    fun handle(message: UpdateMessage) {
        val playerId = PlayerId(message.id)
        val player = room.players.getOrPut(playerId, { Player(playerId, ZERO_XY, ZERO_XY) })
        player.pos = message.pos
    }

    fun handle(message: RemovePlayerMessage) {
        room.players.remove(PlayerId(message.id))
    }

    fun changePos(dxy: XY) {
        val me = room.me()
//        console.log("changePos $me $dxy")
        if (me != null) {
            val newPos = (me.pos + dxy).round()
            networkModule.send(MoveCommand(newPos))
        }
    }

    fun clear() {
        room.clear()
    }
}
