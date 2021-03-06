package client

import org.w3c.dom.CanvasRenderingContext2D
import kotlin.math.PI

class GameView {
    lateinit var ctx: CanvasRenderingContext2D
    var width: Double = 1.0
    var height: Double = 1.0

    fun init(ctx: CanvasRenderingContext2D, width: Int, height: Int) {
        this.width = width.toDouble()
        this.height = height.toDouble()
        this.ctx = ctx
    }

    fun draw(room: Room) {
        ctx.beginPath()
        ctx.fillStyle = "white"
        ctx.rect(0.0, 0.0, width, height)
        ctx.fill()
        ctx.strokeStyle = "#aaaaaa"
        ctx.lineWidth = 1.0
        ctx.stroke()

        room.players.values.forEach { player ->
            ctx.fillStyle = if (player.id == room.myId) "green" else "red"
            ctx.beginPath()
            ctx.arc(player.pos.x, player.pos.y, 10.0, 0.0, 2 * PI)
            ctx.fill()

            ctx.lineWidth = 1.0
            ctx.strokeStyle = "#777777"
            ctx.stroke()
        }
    }
}

