import com.fasterxml.jackson.databind.ObjectMapper
import io.vertx.core.Vertx
import io.vertx.core.http.WebSocket
import io.vertx.kotlin.coroutines.awaitResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.random.Random.Default.nextDouble

suspend fun main() {
    val mapper = createObjectMapper()
    val vertx = Vertx.vertx()
    val scope = CoroutineScope(coroutineContext)
    repeat(100) {
        scope.launch {
            startBot(vertx, mapper)
        }
    }
    delay(Long.MAX_VALUE)
}

private suspend fun startBot(vertx: Vertx, mapper: ObjectMapper) {
    val ws = awaitResult<WebSocket> { handler ->
        vertx.createHttpClient().webSocket(8081, "localhost", "/") {
            if (it.succeeded()) {
                it.result().textMessageHandler {
//                    println(it)
                }
            }
            handler.handle(it)
        }
    }
    println("connected")
    var pos = XY(nextDouble(1000.0), nextDouble(500.0))
    var target = pos
    val inc = 10.0
    while (true) {
        val diff = target - pos
        if (diff.length() > 10) {
            val dxy = diff.normalize() * inc
            pos += dxy
            ws.writeTextMessage(mapper.writeValueAsString(mapOf("x" to pos.x.toInt(), "y" to pos.y.toInt())))
        } else {
            target = XY(nextDouble(1000.0), nextDouble(500.0))
        }
        delay(33)
    }
}

data class XY(val x: Double, val y: Double) {
    operator fun minus(other: XY): XY = XY(x - other.x, y - other.y)
    operator fun times(mul: Double): XY = XY(x * mul, y * mul)
    operator fun plus(other: XY): XY = XY(x + other.x, y + other.y)
    fun normalize(): XY = length().let { XY(x / it, y / it) }
    fun length(): Double = Math.sqrt(x * x + y * y)
}
