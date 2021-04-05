package client

import common.LoginMessage
import common.MoveCommand
import common.RemovePlayerMessage
import common.RoomUpdateMessage
import common.ServerMessage
import common.UpdateMessage
import common.XY
import common.ZERO_XY
import common.plus
import common.round

class GameLogic {
    lateinit var networkModule: NetworkModule
    val room = Room(RoomId(""), PlayerId(""), HashMap())

    fun handle(message: ServerMessage) {
        when (message) {
            is LoginMessage -> room.myId = PlayerId(message.id)
            is RoomUpdateMessage -> {
                room.id = RoomId(message.roomId)
                room.players.clear()
                message.players.forEach { (id, pos) ->
                    val playerId = PlayerId(id)
                    room.players[playerId] = Player(playerId, pos, pos)
                }
            }
            is UpdateMessage -> {
                val playerId = PlayerId(message.id)
                val player = room.players.getOrPut(playerId, { Player(playerId, ZERO_XY, ZERO_XY) })
//                println("move $playerId to ${message.pos}, myId ${room.myId}, eq: ${playerId == room.myId}")
                if (playerId != room.myId) {
                    player.pos = message.pos
                }
            }
            is RemovePlayerMessage -> {
                room.players.remove(PlayerId(message.id))
            }
        }
    }

    fun changePos(dxy: XY) {
        val me = room.me()
//        console.log("changePos $me $dxy")
        if (me != null) {
            val newPos = (me.pos + dxy).round()
            me.pos = newPos
            networkModule.send(MoveCommand(newPos))
        }
    }

    fun clear() {
        room.clear()
    }
}
