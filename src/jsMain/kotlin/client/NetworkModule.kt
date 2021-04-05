package client

import common.ClientCommand
import common.LoginMessage
import common.RemovePlayerMessage
import common.UpdateMessage
import kotlinx.browser.window
import org.w3c.dom.WebSocket

class NetworkModule(val gameLogic: GameLogic) {
    lateinit var socket: WebSocket

    fun connect() {
        val protocol = if (window.location.protocol == "https:") "wss" else "ws"
        val url = "$protocol://${window.location.host}/rooms/${window.location.hash.drop(1)}"
        socket = WebSocket(url)
        socket.onmessage = {
            receive(it.data as String)
        }
    }

    fun disconnect() {
        socket.close()
    }

    fun send(command: ClientCommand) {
        val data = JSON.stringify(command)
        socket.send(data)
    }

    private fun receive(data: String) {
        println("receive: $data")
        when (JSON.parse<dynamic>(data).type) {
            "login" -> {
                val msg = JSON.parse<LoginMessage>(data)
                gameLogic.handle(msg)
            }
            "update" -> {
                val msg = JSON.parse<UpdateMessage>(data)
                gameLogic.handle(msg)
            }
            "remove" -> {
                val msg = JSON.parse<RemovePlayerMessage>(data)
                gameLogic.handle(msg)
            }
            else -> {
                println("unknown message: $data")
            }
        }
    }
}
