import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.WebSocket
import kotlin.js.json

var x = 0.0
var y = 0.0
var lastTime: Double? = null
var socket: WebSocket? = null

fun main() {
    window.onkeydown = {
        val inc = 10
//        window.alert("onkeydown ${it.keyCode}")
        console.log("onkeydown ${it.keyCode}")
        when (it.keyCode) {
            37 -> changePos(x - inc, y) // left
            38 -> changePos(x, y - inc) // up
            39 -> changePos(x + inc, y) // right
            40 -> changePos(x, y + inc) // down
        }
    }
    window.onload = {
        window.requestAnimationFrame(::draw)
        connect()
    }
}

private fun changePos(x: Double, y: Double) {
    socket?.send(JSON.stringify(json("x" to x.toInt(), "y" to y.toInt())))
}

private fun connect() {
    socket = WebSocket("ws://localhost:8081")
    socket?.onmessage = {
//            window.alert("Received data from websocket: " + it.data)
        val cmd: dynamic = JSON.parse(it.data as String)
        if (cmd.type == "update") {
            x = cmd.x
            y = cmd.y
        }
        null
    }

//        socket.onopen = {
//            window.alert("Web Socket opened")
//            socket.send("""{ "x": 200, "y": 200}""")
//        }

//        socket.onclose = {
//            window.alert("Web Socket closed")
//        }
}

private fun draw(timestamp: Double) {
    val canvas = document.getElementById("canvas") as HTMLCanvasElement
    val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
    val dt = timestamp - (lastTime ?: timestamp)
//    x = (x + dt) % canvas.width
    lastTime = timestamp
    ctx.fillStyle = "white"
    ctx.fillRect(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
    ctx.fillStyle = "green"
    ctx.fillRect(x, y, 20.0, 20.0)
    window.requestAnimationFrame(::draw)
}
