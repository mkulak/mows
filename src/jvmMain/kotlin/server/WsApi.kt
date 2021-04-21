package server

import common.ClientCommand
import common.ServerMessage
import io.vertx.core.Handler
import io.vertx.core.http.ServerWebSocket
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KLogging

class WsApi(val json: Json) : Handler<ServerWebSocket> {
    private val clients = mutableMapOf<PlayerId, ConnectedClient>()
    lateinit var gameService: GameService

    override fun handle(ws: ServerWebSocket) {
        if (!ws.path().startsWith("/rooms/"))  {
            logger.warn("Request path doesn't match: ${ws.path()}")
        }
        ws.accept()
        val playerId = PlayerId(nextId())
        val client = ConnectedClient(playerId, ws)
        clients[playerId] = client
        ws.textMessageHandler { msg ->
            handleClientCommand(msg, playerId)
        }
        ws.closeHandler {
            gameService.onLeave(playerId)
            clients.remove(playerId)
        }
        val roomId = RoomId(ws.path().removePrefix("/rooms/")).takeIf { it.value.isNotEmpty() }
        gameService.onJoin(playerId, roomId)
    }

    private fun handleClientCommand(msg: String, playerId: PlayerId) {
//        logger.info("Got $msg from $playerId")
        val command = json.decodeFromString<ClientCommand>(msg)
        gameService.handle(playerId, command)
    }

    fun send(playerIds: Set<PlayerId>, message: ServerMessage) {
        val data = json.encodeToString(message)
        playerIds.forEach {
            clients[it]?.ws?.writeTextMessage(data)
        }
    }

    fun send(playerId: PlayerId, message: ServerMessage) {
        clients[playerId]?.ws?.writeTextMessage(json.encodeToString(message))
    }

    companion object : KLogging()
}
