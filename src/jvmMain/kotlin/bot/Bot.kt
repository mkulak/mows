import common.ClientCommand
import common.MoveCommand
import common.XY
import common.length
import common.minus
import common.normalize
import common.plus
import common.times
import io.vertx.core.Vertx
import io.vertx.core.http.WebSocket
import io.vertx.core.http.WebSocketConnectOptions
import io.vertx.kotlin.coroutines.awaitResult
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.round
import kotlin.random.Random.Default.nextDouble
import kotlin.random.Random.Default.nextLong

val json = Json { ignoreUnknownKeys = true }

suspend fun main() {
    val vertx = Vertx.vertx()
//    val scope = CoroutineScope(coroutineContext)
    var botCount = 0
    repeat(10) { room ->
        repeat(room * 2) { bot ->
            GlobalScope.launch { startBot(vertx, bot, room) }
            botCount++
        }
    }
    println("launched $botCount")
    delay(Long.MAX_VALUE)
}

private suspend fun startBot(vertx: Vertx, bot: Int, room: Int) {
    val ws = awaitResult<WebSocket> { handler ->
        val options = WebSocketConnectOptions()
//            .setSsl(true)
            .setSsl(false)
//            .setHost("wonder.kvarto.net")
            .setHost("localhost")
//            .setPort(443)
            .setPort(8080)
            .setURI("/rooms/$room")
        vertx.createHttpClient().webSocket(options) {
//            if (it.succeeded()) {
//                it.result().textMessageHandler {
//                    println(it)
//                }
//            }
            handler.handle(it)
        }
    }
    println("connected bot#$bot room#$room")
    var pos = XY(nextDouble(1000.0), nextDouble(500.0))
    var target = pos
    val inc = 125.0
    delay(nextLong(1000))
    while (true) {
        val diff = target - pos
        val diffLen = diff.length()
        if (diffLen > 1) {
            val dxy = diff.normalize() * inc.coerceAtMost(diffLen)
            pos += dxy
//            println("sending pos $pos")
            ws.writeTextMessage(json.encodeToString(MoveCommand(pos) as ClientCommand))
        } else {
            target = XY(round(nextDouble(1000.0)), round(nextDouble(500.0)))
        }
        delay(250)
    }
}

// bots: 90, top cpu: 62%, top mem: 380 Mb, traffic out: 470 Kib/s, traffic in: 145 Kib/s
