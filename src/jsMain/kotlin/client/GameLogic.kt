package client

import common.AddPlayerMessage
import common.FullRoomUpdateMessage
import common.LoginMessage
import common.MoveCommand
import common.PongCommand
import common.RemovePlayerMessage
import common.ServerMessage
import common.UpdateMessage
import common.XY
import common.ZERO_XY
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
            is AddPlayerMessage -> addPlayer(message.id, message.pos)
            is RemovePlayerMessage -> room.players.remove(PlayerId(message.id))
            is FullRoomUpdateMessage -> {
                room.id = RoomId(message.roomId)
                room.players.clear()
                message.players.forEach { (id, pos) ->
                    addPlayer(id, pos)
                }
            }
            is UpdateMessage -> {
                message.ids.forEachIndexed { index, id ->
                    val player = room.players[PlayerId(id)]!!
                    player.serverPos = XY(message.xs[index], message.ys[index])
                }
            }
            is PongCommand -> Unit
        }
    }

    private fun addPlayer(id: String, pos: XY) {
        val playerId = PlayerId(id)
        room.players[playerId] = Player(playerId, pos, pos)
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
        it.pos += move(it.pos, it.serverPos, dt, false)
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

fun move(from: XY, to: XY, dt: Double, me: Boolean): XY {
    val diff = to - from
    val diffLen = diff.length()
    return when {
        diffLen < 1 -> ZERO_XY
        else -> {
            val dir = diff.normalize()
            val koef = if (me) 1.0 else OTHER_SPEED_MUL
            val velocity = MOVE_SPEED * koef * (0.1 + (diffLen / 25.0).coerceAtMost(1.0) * 0.9)
            dir * dt * velocity
        }
    }
}

val POS_UPDATE_RATE = 300.0
val MOVE_SPEED = 0.4
val OTHER_SPEED_MUL = 0.8
