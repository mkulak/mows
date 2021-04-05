package server

import common.ClientCommand
import common.LoginMessage
import common.MoveCommand
import common.RemovePlayerMessage
import common.RoomUpdateMessage
import common.UpdateMessage
import common.XY
import mu.KLogging
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
            Room(actualRoomId, HashSet())
        }
        room.playerIds += playerId
        logger.info("$playerId entered $actualRoomId, size: ${room.playerIds.size}")
        val message = RoomUpdateMessage(room.id.value, room.playerIds.associate { it.value to players[it]!!.pos })
        wsApi.send(playerId, LoginMessage(playerId.value))
        wsApi.send(playerId, message)
        room.playerIds.forEach {
            wsApi.send(it, UpdateMessage(playerId.value, pos))
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
                wsApi.send(updatedPlayer.room.playerIds, UpdateMessage(playerId.value, updatedPlayer.pos))
            }
        }
    }

    private val Player.room: Room get() = rooms[roomId]!!

    companion object : KLogging()
}
