import client.GameLogic
import client.GameView
import client.InputModule
import client.NetworkModule
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.floor

var lastTime: Double? = null

val gameLogic = GameLogic()
val networkModule = NetworkModule(gameLogic)
val inputModule = InputModule(gameLogic)
val view = GameView()

fun main() {
    window.onhashchange = {
        gameLogic.clear()
        networkModule.disconnect()
        networkModule.connect()
    }
    window.onload = {
        val canvas = document.getElementById("root") as HTMLCanvasElement
        val w = 1000
        val h = 500
        val scale = window.devicePixelRatio
        val scaledWidth = floor(w * scale).toInt()
        val scaledHeight = floor(h * scale).toInt()
        canvas.style.width = "${w}px"
        canvas.style.height = "${h}px"
        canvas.width = scaledWidth
        canvas.height = scaledHeight
        val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
        ctx.scale(scale, scale)

        view.init(ctx, canvas.width, canvas.height)
        gameLogic.networkModule = networkModule
        inputModule.init()
        networkModule.connect()
        window.requestAnimationFrame(::tick)
    }
}

private fun tick(timestamp: Double) {
    val dt = timestamp - (lastTime ?: timestamp)
    lastTime = timestamp
    inputModule.handle(dt)
    gameLogic.update(dt)
    view.draw(gameLogic.room)
    window.requestAnimationFrame(::tick)
}
