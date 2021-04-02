import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.WebSocket
import kotlin.js.json
import kotlin.math.PI

var lastTime: Double? = null
var socket: WebSocket? = null
var myId = ""
val players = mutableMapOf<String, Point>()

data class Point(val x: Double, val y: Double)

fun main() {
    window.onkeydown = {
        val inc = 10.0
//        window.alert("onkeydown ${it.keyCode}")
        console.log("onkeydown ${it.keyCode}")
        when (it.keyCode) {
            37 -> changePos(-inc, 0.0) // left
            38 -> changePos(0.0, - inc) // up
            39 -> changePos(inc, 0.0) // right
            40 -> changePos(0.0, inc) // down
        }
        it.preventDefault()
    }
    window.onload = {
        window.requestAnimationFrame(::draw)
        connect()
    }
}

private fun changePos(dx: Double, dy: Double) {
    val me = players[myId]
    if (me != null) {
        socket?.send(JSON.stringify(json("x" to (me.x + dx).toInt(), "y" to (me.y + dy).toInt())))
    }
}

private fun connect() {
    socket = WebSocket("ws://localhost:8081")
    socket?.onmessage = {
        val cmd: dynamic = JSON.parse(it.data as String)
        when (cmd.type)  {
            "login" -> {
                myId = cmd.id
                players[cmd.id] = Point(cmd.x, cmd.y)
            }
            "update" -> players[cmd.id] = Point(cmd.x, cmd.y)
        }
        null
    }
}

private fun draw(timestamp: Double) {
    val canvas = document.getElementById("root") as HTMLCanvasElement
    val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
    val dt = timestamp - (lastTime ?: timestamp)
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
        ctx.arc(pos.x ,pos.y, 10.0, 0.0, 2 * PI)
        ctx.fill()

        ctx.lineWidth = 1.0
        ctx.strokeStyle = "#003300"
        ctx.stroke()
    }

    window.requestAnimationFrame(::draw)
}
