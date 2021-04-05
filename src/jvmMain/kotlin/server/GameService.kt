package server

import common.ClientCommand
import common.LoginMessage
import common.MoveCommand
import common.RemovePlayerMessage
import common.UpdateMessage
import common.XY
import mu.KLogging
import kotlin.random.Random.Default.nextDouble

class GameService(val wsApi: WsApi) {
    val players = mutableMapOf<PlayerId, Player>()

    fun onJoin(playerId: PlayerId) {
        val pos = XY(nextDouble(MAP_WIDTH), nextDouble(MAP_HEIGHT))
        players[playerId] = Player(playerId, pos)
        wsApi.send(playerId, LoginMessage(playerId.value))
        wsApi.broadcast(UpdateMessage(playerId.value, pos))
        players.values.forEach {
            if (it.id != playerId) {
                wsApi.send(playerId, UpdateMessage(it.id.value, it.pos))
            }
        }
    }

    fun onLeave(playerId: PlayerId) {
        players.remove(playerId)
        wsApi.broadcast(RemovePlayerMessage(playerId.value))
    }

    fun handle(playerId: PlayerId, command: ClientCommand) {
        val player = players[playerId]
        if (player == null) {
            logger.warn("Unknown client $playerId")
            return
        }
        when (command) {
            is MoveCommand -> {
                val updatedPlayer = player.copy(pos = command.pos)
                players[playerId] = updatedPlayer
                wsApi.broadcast(UpdateMessage(playerId.value, updatedPlayer.pos))
            }
        }
    }

    companion object : KLogging()
}
