import client.GameLogic
import client.GameView
import client.InputModule
import client.NetworkModule
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement

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
        view.init(document.getElementById("root") as HTMLCanvasElement)
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
    view.draw(gameLogic.room)
    window.requestAnimationFrame(::tick)
}
