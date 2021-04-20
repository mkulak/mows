package client

import common.ClientCommand
import common.ServerMessage
import kotlinx.browser.window
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.w3c.dom.WebSocket

class NetworkModule(val gameLogic: GameLogic) {
    val json = Json { ignoreUnknownKeys = true }
    lateinit var socket: WebSocket

    fun connect() {
        val protocol = if (window.location.protocol == "https:") "wss" else "ws"
        val url = "$protocol://${window.location.host.replace("3000", "7000")}/rooms/${window.location.hash.drop(1)}"
        socket = WebSocket(url)
        socket.onmessage = {
            receive(it.data as String)
        }
    }

    fun disconnect() {
        socket.close()
    }

    fun send(command: ClientCommand) {
        val data = json.encodeToString(command)
        socket.send(data)
    }

    private fun receive(data: String) {
//        println("receive: $data")
        val msg = try {
            json.decodeFromString<ServerMessage>(data)
        } catch (e: Exception) {
            println("Unknown server message: $data")
            return
        }
        gameLogic.handle(msg)
    }
}
