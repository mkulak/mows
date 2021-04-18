import common.ClientCommand
import common.LoginMessage
import common.MoveCommand
import common.ServerMessage
import common.UpdateMessage
import common.XY
import common.length
import common.minus
import common.normalize
import common.plus
import common.round
import common.times
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.http.WebSocket
import io.vertx.core.http.WebSocketConnectOptions
import io.vertx.kotlin.coroutines.awaitResult
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.micrometer.MicrometerMetricsOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import server.MAP_HEIGHT
import server.MAP_WIDTH
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.random.Random
import kotlin.random.Random.Default.nextDouble
import kotlin.random.Random.Default.nextLong
import kotlin.random.asJavaRandom
import kotlin.time.ExperimentalTime

val json = Json { ignoreUnknownKeys = true }
val speed = 100.0
var sentCount = 0
var sentSize = 0L
var receivedCount = 0
var receivedSize = 0L
var latencyMeasured = 0
val registry = SimpleMeterRegistry()
val timer = Timer.builder("my-latency")
    .description("net latency")
    .publishPercentiles(0.5, 0.95, 0.99, 0.999)
    .publishPercentileHistogram()
    .register(registry)
val vertx = Vertx.vertx(VertxOptions().apply {
    metricsOptions = MicrometerMetricsOptions().apply {
        micrometerRegistry = registry
        isEnabled = true
    }
})

@ExperimentalTime
suspend fun main() {
    val scope = CoroutineScope(vertx.dispatcher())
    val botCount = 300
    val duration = 10
    val rooms = 10
    repeat(botCount) { bot ->
        val room = bot % rooms
        scope.launch { Bot(vertx, bot, true).start(room) }
    }
    delay(duration * 1000L)
    scope.coroutineContext[Job]?.cancelChildren()
    vertx.close()
    println("bots: $botCount")
    println("rooms: $rooms")
    println("duration: ${duration}s")
    println("sent packets: $sentCount (${sentCount / duration} packets/s)")
    println("received packets: $receivedCount (${receivedCount / duration} packets/s)")
    println("total bytes sent: $sentSize (${sentSize / sentCount} bytes/packet)")
    println("total bytes received: $receivedSize (${receivedSize / receivedCount} bytes/packet)")
    println("vertx bytes read: ${registry.counter("vertx.http.client.bytes.read").count()}")
    println("latency measurements: $latencyMeasured")
    timer.takeSnapshot().percentileValues().forEach {
        println("${it.percentile()} percentile - ${it.value().toLong() / 1e6} ms")
    }
//    println(MetricsService.create(vertx).getMetricsSnapshot(""))
}

class Bot(val vertx: Vertx, val bot: Int, val walking: Boolean) {
    var myId = ""
    val pos2time = HashMap<XY, Long>()
    var pos = randomPoint()
    var target = randomPoint()

    suspend fun start(room: Int) {
        val ws = connect(room, vertx)
//        println("connected bot#$bot room#$room")
        delay(nextLong(1000))
        while (walking) {
            advancePos()
            sendPos(ws, pos)
            delay(200)
        }
        delay(Long.MAX_VALUE)
    }

    private suspend fun connect(room: Int, vertx: Vertx): WebSocket =
        awaitResult { handler ->
            val options = WebSocketConnectOptions()
                .setSsl(false)
//            .setHost("wonder.kvarto.net")
                .setHost("ec2-18-156-174-230.eu-central-1.compute.amazonaws.com")
//                .setHost("localhost")
//                .setPort(8080)
                .setPort(7000)
                .setURI("/rooms/$room")
            vertx.createHttpClient().webSocket(options) {
                it.result()?.textMessageHandler(::handleMessage)
                handler.handle(it)
            }
        }

    private fun handleMessage(res: String) {
        receivedCount++
        receivedSize += res.length
        val msg = json.decodeFromString<ServerMessage>(res)
        when (msg) {
            is LoginMessage -> myId = msg.id
            is UpdateMessage -> {
                val index = msg.ids.indexOf(myId)
                if (index != -1) {
                    val pos = XY(msg.xs[index], msg.ys[index])
                    val sentAt = pos2time.remove(pos)
                    if (sentAt != null) {
                        timer.record(System.currentTimeMillis() - sentAt, MILLISECONDS)
                        latencyMeasured++
                    }
                }
            }
        }
    }

    private fun sendPos(ws: WebSocket, pos: XY) {
        if (pos !in pos2time) {
            pos2time[pos] = System.currentTimeMillis()
        }
        val str = json.encodeToString(MoveCommand(pos) as ClientCommand)
        ws.writeTextMessage(str)
        sentCount++
        sentSize += str.length
    }

    private fun randomPoint() = XY(nextDouble(MAP_WIDTH), nextDouble(MAP_HEIGHT)).round()

    private tailrec fun advancePos() {
        val diff = target - pos
        val diffLen = diff.length()
        if (diffLen > 1) {
            pos += diff.normalize() * speed.coerceAtMost(diffLen)
        } else {
            target = randomPoint()
            advancePos()
        }
    }
}

// bots: 90, top cpu: 62%, top mem: 380 Mb, traffic out: 470 Kib/s, traffic in: 145 Kib/s
//bots: 10
//rooms: 1
//duration: 10s
//sent packets: 436 (43 packets/s)
//received packets: 975 (97 packets/s)
//total bytes sent: 27140 (62 bytes/packet)
//total bytes received: 147326 (151 bytes/packet)
//vertx bytes read: 122880.0
//latency measurements: 94
//0.5 percentile - 82.837504 ms
//0.95 percentile - 128.974848 ms
//0.99 percentile - 133.169152 ms
//0.999 percentile - 133.169152 ms


//bots: 100
//rooms: 1
//duration: 10 s
//sent packets: 3369 (336 packets/s)
//received packets: 12236 (1223 packets/s)
//total bytes sent: 209118 (62 bytes/packet)
//total bytes received: 7419488 (606 bytes/packet)
//vertx bytes read: 7208960.0
//latency measurements: 732
//0.5 percentile - 103.809024 ms
//0.95 percentile - 149.946368 ms
//0.99 percentile - 258.998272 ms
//0.999 percentile - 418.381824 ms

//bots: 300
//rooms: 1
//duration: 10s
//sent packets: 3658 (365 packets/s)
//received packets: 15441 (1544 packets/s)
//total bytes sent: 228041 (62 bytes/packet)
//total bytes received: 9147292 (592 bytes/packet)
//vertx bytes read: 8904704.0
//latency measurements: 742
//0.5 percentile - 116.391936 ms
//0.95 percentile - 519.04512 ms
//0.99 percentile - 837.812224 ms
//0.999 percentile - 1039.138816 ms

//bots: 300
//rooms: 10
//duration: 10s
//sent packets: 3680 (368 packets/s)
//received packets: 8255 (825 packets/s)
//total bytes sent: 229878 (62 bytes/packet)
//total bytes received: 1190307 (144 bytes/packet)
//vertx bytes read: 978944.0
//latency measurements: 761
//0.5 percentile - 82.837504 ms
//0.95 percentile - 133.169152 ms
//0.99 percentile - 158.334976 ms
//0.999 percentile - 175.112192 ms
