package client

import common.AddPlayerMessage
import common.FullRoomUpdateMessage
import common.LoginMessage
import common.MoveCommand
import common.RemovePlayerMessage
import common.ServerMessage
import common.UpdateMessage
import common.XY
import common.length
import common.minus
import common.normalize
import common.plus
import common.round
import common.times

class GameLogic {
    lateinit var networkModule: NetworkModule
    val room = Room(RoomId(""), PlayerId(""), HashMap())
    var elapsedSinceLastPosUpdate = 0.0

    fun handle(message: ServerMessage) {
        when (message) {
            is LoginMessage -> room.myId = PlayerId(message.id)
            is FullRoomUpdateMessage -> {
                room.id = RoomId(message.roomId)
                room.players.clear()
                message.players.forEach { (id, pos) ->
                    val playerId = PlayerId(id)
                    room.players[playerId] = Player(playerId, pos, pos)
                }
            }
            is UpdateMessage -> {
                message.ids.forEachIndexed { index, id ->
                    val playerId = PlayerId(id)
                    val player = room.players[playerId]!!//.getOrPut(playerId, { Player(playerId, message.pos, message.pos) })
                    player.serverPos = XY(message.xs[index], message.ys[index])
                }
            }
            is AddPlayerMessage -> {
                val playerId = PlayerId(message.id)
                room.players.put(playerId, Player(playerId, message.pos, message.pos))
            }
            is RemovePlayerMessage -> {
                room.players.remove(PlayerId(message.id))
            }
        }
    }

    fun update(dt: Double) {
        room.players.values.forEach {
            if (it.id == room.myId) {
                updateServerPosition(it, dt)
            } else {
                updateLocalPosition(it, dt)
            }
        }
    }

    private fun updateServerPosition(me: Player, dt: Double) {
        if (me.pos == me.serverPos) {
            return
        }
        elapsedSinceLastPosUpdate += dt
        if (elapsedSinceLastPosUpdate > POS_UPDATE_RATE) {
            elapsedSinceLastPosUpdate = 0.0
            println("sending my new position: ${me.pos}")
            networkModule.send(MoveCommand(me.pos))
        }
    }

    private fun updateLocalPosition(it: Player, dt: Double) {
        val diff = it.serverPos - it.pos
        val diffLen = diff.length()
        if (diffLen > 1) {
            it.pos += if (diffLen > dt) diff.normalize() * dt * MOVE_SPEED * OTHER_SPEED_MUL else diff
        }
    }

    fun changePos(dxy: XY) {
        val me = room.me()
//        console.log("changePos $me $dxy")
        if (me != null) {
            val newPos = (me.pos + dxy).round()
            me.pos = newPos
        }
    }

    fun clear() {
        room.clear()
    }
}

val POS_UPDATE_RATE = 250.0
val MOVE_SPEED = 0.5
val OTHER_SPEED_MUL = 0.8
