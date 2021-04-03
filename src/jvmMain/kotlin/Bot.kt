import com.fasterxml.jackson.databind.ObjectMapper
import io.vertx.core.Vertx
import io.vertx.core.http.WebSocket
import io.vertx.kotlin.coroutines.awaitResult
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random.Default.nextDouble
import kotlin.random.Random.Default.nextLong

suspend fun main() {
    val mapper = createObjectMapper()
    val vertx = Vertx.vertx()
//    val scope = CoroutineScope(coroutineContext)
    repeat(100) {
        GlobalScope.launch {
            startBot(vertx, mapper)
        }
    }
    delay(Long.MAX_VALUE)
}

private suspend fun startBot(vertx: Vertx, mapper: ObjectMapper) {
    val ws = awaitResult<WebSocket> { handler ->
        vertx.createHttpClient().webSocket(8080, "localhost", "/") {
//            if (it.succeeded()) {
//                it.result().textMessageHandler {
//                    println(it)
//                }
//            }
            handler.handle(it)
        }
    }
    println("connected")
    var pos = XY(nextDouble(1000.0), nextDouble(500.0))
    var target = pos
    val inc = 10.0
    delay(nextLong(1000))
    while (true) {
        val diff = target - pos
        if (diff.length() > 10) {
            val dxy = diff.normalize() * inc
            pos += dxy
            ws.writeTextMessage(mapper.writeValueAsString(MoveCommand(pos)))
        } else {
            target = XY(nextDouble(1000.0), nextDouble(500.0))
        }
        delay(nextLong(1000))
    }
}
