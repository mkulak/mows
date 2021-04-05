package client

import common.XY
import common.ZERO_XY
import common.length
import common.minus
import common.normalize
import common.times
import kotlinx.browser.window
import org.w3c.dom.events.KeyboardEvent

class InputModule(val gameLogic: GameLogic) {
    var isMouseDown = false
    var lastMousePos = ZERO_XY
    var gamepadIndex: Int? = null
    val step = 10.0

    fun init() {
        window.onkeydown = {
            handleKeydown(it)
        }
        window.onmousedown = {
            isMouseDown = true
            null
        }
        window.onmouseup = {
            isMouseDown = false
            null
        }
        window.onmousemove = {
            lastMousePos = XY(it.offsetX, it.offsetY)
            null
        }
        window.addEventListener("gamepadconnected", { event: dynamic ->
//            println("gamepad connected $event ${event.gamepad.index}")
            gamepadIndex = event.gamepad.index
        })
        window.addEventListener("gamepaddisconnected", {
//            println("gamepad disconnected $it")
            gamepadIndex = null
        })
    }

    private fun handleKeydown(it: KeyboardEvent) {
        val dxy = when (it.keyCode) {
            37 -> XY(-1.0, 0.0) // left
            38 -> XY(0.0, -1.0) // up
            39 -> XY(1.0, 0.0) // right
            40 -> XY(0.0, 1.0) // down
            else -> null
        }
        if (dxy != null) {
            gameLogic.changePos(dxy * step)
            it.preventDefault()
        }
    }

    fun handle(dt: Double) {
        tryMove(dt)
        tryMoveViaGamepad(dt)
    }

    private fun tryMove(dt: Double) {
        val myPos = gameLogic.room.me()?.pos
        if (myPos != null && isMouseDown) {
            val dxy = lastMousePos - myPos
            val length = dxy.length()
            if (length > 1) {
                gameLogic.changePos(dxy.normalize() * dt.coerceAtMost(length))
            }
        }
    }

    private fun tryMoveViaGamepad(dt: Double) {
        if (gamepadIndex == null) {
            return
        }
        val gamepad = js("navigator").getGamepads()[gamepadIndex]
        val dxy = XY(gamepad.axes[0], gamepad.axes[1])
        if (dxy.length() > 0.5) {
            println("gamepad dxy: $dxy")
            gameLogic.changePos(dxy.normalize() * dt)
        }
        if (gamepad.buttons.iterator().asSequence().any { it.pressed }) {
            gamepad.vibrationActuator.playEffect(
                "dual-rumble",
                js("{ duration: 100, strongMagnitude: 1.0, weakMagnitude: 1.0 }")
            )
        }
    }
}
