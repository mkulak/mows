package server

import common.AddPlayerMessage
import common.ClientCommand
import common.FullRoomUpdateMessage
import common.LoginMessage
import common.MoveCommand
import common.RemovePlayerMessage
import common.UpdateMessage
import common.XY
import mu.KLogging
import kotlin.math.round
import kotlin.random.Random.Default.nextDouble

class GameService(val wsApi: WsApi) {
    val players = mutableMapOf<PlayerId, Player>()
    val rooms = mutableMapOf<RoomId, Room>()

    fun onJoin(playerId: PlayerId, requestedRoomId: RoomId?) {
        val pos = XY(nextDouble(MAP_WIDTH), nextDouble(MAP_HEIGHT))
        val actualRoomId = requestedRoomId ?: rooms.values.minByOrNull { it.playerIds.size }?.id ?: RoomId(nextId())
        players[playerId] = Player(playerId, actualRoomId, pos)
        val room = rooms.getOrPut(actualRoomId) {
            logger.info("creating new room: $actualRoomId")
            Room(actualRoomId, HashSet(), HashSet(), 0)
        }
        room.playerIds += playerId
        logger.info("$playerId entered $actualRoomId, size: ${room.playerIds.size}")
        val message = FullRoomUpdateMessage(room.id.value, room.playerIds.associate { it.value to players[it]!!.pos })
        wsApi.send(playerId, LoginMessage(playerId.value))
        wsApi.send(playerId, message)
        room.playerIds.forEach {
            wsApi.send(it, AddPlayerMessage(playerId.value, pos))
        }
    }

    fun onLeave(playerId: PlayerId) {
        val player = players.remove(playerId)
        if (player == null) {
            logger.warn("Unknown player is leaving: $playerId")
            return
        }
        val room = player.room
        room.playerIds.remove(playerId)
        room.playersWithUpdates.remove(playerId)
        if (room.playerIds.isNotEmpty()) {
            wsApi.send(room.playerIds, RemovePlayerMessage(playerId.value))
        } else {
            rooms.remove(player.roomId)
        }
    }

    fun handle(playerId: PlayerId, command: ClientCommand) {
        val player = players[playerId]
        if (player == null) {
            logger.warn("Unknown player's command: $playerId")
            return
        }
        when (command) {
            is MoveCommand -> {
                val updatedPlayer = player.copy(pos = command.pos)
                players[playerId] = updatedPlayer
                updatedPlayer.room.playersWithUpdates += playerId
            }
        }
    }

    fun tick()  {
        for (room in rooms.values) {
            val now = System.currentTimeMillis()
//            if (room.lastUpdateSentAt + UPDATE_SEND_INTERVAL > now) {
//                continue
//            }
            if (room.playersWithUpdates.isNotEmpty()) {
                val ids = ArrayList<String>()
                val xs = ArrayList<Double>()
                val ys = ArrayList<Double>()
                room.playersWithUpdates.forEach {
                    ids += it.value
                    val pos = players[it]!!.pos
                    xs += round(pos.x)
                    ys += round(pos.y)
                }
                wsApi.send(room.playerIds, UpdateMessage(ids, xs, ys))
                room.playersWithUpdates.clear()
            }
            room.lastUpdateSentAt = now
        }
    }

    private val Player.room: Room get() = rooms[roomId]!!

    companion object : KLogging()
}
