package server

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import common.MoveCommand
import common.ServerMessage
import io.vertx.core.Handler
import io.vertx.core.http.ServerWebSocket
import mu.KLogging

class WsApi(val mapper: ObjectMapper) : Handler<ServerWebSocket> {
    private val clients = mutableMapOf<PlayerId, ConnectedClient>()
    lateinit var gameService: GameService

    override fun handle(ws: ServerWebSocket) {
        ws.accept()
        val playerId = PlayerId(nextId())
        val client = ConnectedClient(playerId, ws)
        clients[playerId] = client
        logger.info("Joined $playerId on ${ws.path()}")
        ws.textMessageHandler { msg ->
            handleClientCommand(msg, playerId)
        }
        ws.closeHandler {
            logger.info("Disconnected $playerId")
            gameService.onLeave(playerId)
            clients.remove(playerId)
        }
        gameService.onJoin(playerId)
    }

    private fun handleClientCommand(msg: String, playerId: PlayerId) {
//        logger.info("Got $msg from $playerId")
        val command = mapper.readValue<MoveCommand>(msg)
        gameService.handle(playerId, command)
    }

    fun broadcast(message: ServerMessage) {
        val data = mapper.writeValueAsString(message)
        clients.values.forEach {
            it.ws.writeTextMessage(data)
        }
    }

    fun send(playerId: PlayerId, message: ServerMessage) {
        clients[playerId]?.ws?.writeTextMessage(mapper.writeValueAsString(message))
    }

    companion object : KLogging()
}
