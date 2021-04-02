import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.WebSocket
import kotlin.math.PI

var lastTime: Double? = null
var socket: WebSocket? = null
var myId = ""
val players = mutableMapOf<String, XY>()

fun main() {
    window.onkeydown = {
        val inc = 10.0
//        window.alert("onkeydown ${it.keyCode}")
        console.log("onkeydown ${it.keyCode}")
        val dxy = when (it.keyCode) {
            37 -> XY(-inc, 0.0) // left
            38 -> XY(0.0, -inc) // up
            39 -> XY(inc, 0.0) // right
            40 -> XY(0.0, inc) // down
            else -> XY(0.0, 0.0)
        }
        changePos(dxy)
        it.preventDefault()
    }
    window.onload = {
        window.requestAnimationFrame(::draw)
        connect()
    }
}

private fun changePos(dxy: XY) {
    val me = players[myId]
    console.log("changePos $me $dxy")
    if (me != null) {
        val newPos = me + dxy
        val data = JSON.stringify(MoveCommand(newPos))
        console.log("changePos data $data")
        socket?.send(data)
    }
}

private fun connect() {
    socket = WebSocket("ws://localhost:8081")
    socket?.onmessage = {
        console.log("received ${it.data}")
        val data = it.data as String
        when (JSON.parse<dynamic>(data).type) {
            "login" -> {
                val message = JSON.parse<LoginMessage>(data)
                println("received login: $message")
                myId = message.id
            }
            "update" -> {
                val message = JSON.parse<UpdateMessage>(data)
                println("received update: $message")
                players[message.id] = message.pos
            }
            "remove" -> {
                val message = JSON.parse<RemovePlayerMessage>(data)
                println("received remove: $message")
                players.remove(message.id)
            }
        }
        null
    }
}

private fun draw(timestamp: Double) {
    val canvas = document.getElementById("root") as HTMLCanvasElement
    val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
//    val dt = timestamp - (lastTime ?: timestamp)
//    x = (x + dt) % canvas.width
    lastTime = timestamp
    ctx.beginPath()
    ctx.fillStyle = "white"
    ctx.rect(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
    ctx.fill()
    ctx.strokeStyle = "#aaaaaa"
    ctx.lineWidth = 1.0
    ctx.stroke()

    players.forEach { (id, pos) ->
        ctx.fillStyle = if (id == myId) "green" else "red"
        ctx.beginPath()
        ctx.arc(pos.x, pos.y, 10.0, 0.0, 2 * PI)
        ctx.fill()

        ctx.lineWidth = 1.0
        ctx.strokeStyle = "#777777"
        ctx.stroke()
    }

    window.requestAnimationFrame(::draw)
}
