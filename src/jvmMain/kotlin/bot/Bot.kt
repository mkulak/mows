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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import server.MAP_HEIGHT
import server.MAP_WIDTH
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.coroutines.coroutineContext
import kotlin.random.Random.Default.nextDouble
import kotlin.random.Random.Default.nextLong
import kotlin.time.ExperimentalTime

val json = Json { ignoreUnknownKeys = true }
val speed = 100.0
//var sentCount = 0
//var sentSize = 0L
//var receivedCount = 0
//var receivedSize = 0L
//var latencyMeasured = 0
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
suspend fun main(args: Array<String>) {
    val scope = CoroutineScope(Dispatchers.Default)
    val botCount = args.firstOrNull()?.toInt() ?: 300
    val duration = 60
    val rooms = 1
    val bots = List(botCount) { bot ->
        val room = bot % rooms
        Bot(vertx, bot, room, true)
    }
    val tasks = bots.map {
        scope.launch {
            it.start()
        }
    }
    println("started waiting")
    delay(duration * 1000L)
    scope.coroutineContext[Job]?.cancelChildren()
    println("shutting down")
    tasks.forEach { it.join() }
    vertx.close()
    printResults(bots, rooms, duration)
}

private fun printResults(bots: List<Bot>, rooms: Int, duration: Int) {
    val sentCount = bots.sumBy { it.sentCount }
    val sentSize = bots.sumByLong { it.sentSize }
    val receivedCount = bots.sumBy { it.receivedCount }
    val receivedSize = bots.sumByLong { it.receivedSize }
    val latencyMeasured = bots.sumBy { it.latencyMeasured }
    val pendingLatency = bots.sumBy { it.pos2time.size }
    println("bots: ${bots.size}")
    println("rooms: $rooms")
    println("duration: ${duration}s")
    println("sent packets: $sentCount (${sentCount / duration} packets/s)")
    println("received packets: $receivedCount (${receivedCount / duration} packets/s)")
    println("total bytes sent: $sentSize (${sentSize / sentCount} bytes/packet)")
    println("total bytes received: $receivedSize (${receivedSize / receivedCount} bytes/packet)")
    println("vertx bytes read: ${registry.counter("vertx.http.client.bytes.read").count()}")
    println("latency measurements: $latencyMeasured")
    println("pending latency measurements: $pendingLatency")
    timer.takeSnapshot().percentileValues().forEach {
        println("${it.percentile()} percentile - ${it.value().toLong() / 1e6} ms")
    }
}

class Bot(val vertx: Vertx, val bot: Int, val room: Int, val walking: Boolean) {
    var myId = ""
    val pos2time = HashMap<XY, Long>()
    var pos = randomPoint()
    var target = randomPoint()

    var sentCount = 0
    var sentSize = 0L
    var receivedCount = 0
    var receivedSize = 0L
    var latencyMeasured = 0

    suspend fun start() {
        val ws = connect(room, vertx)
//        println("connected bot#$bot room#$room")
        delay(nextLong(1000))
        while (coroutineContext.isActive) {
            if (walking) {
                advancePos()
                sendPos(ws, pos)
                delay(200)
            } else {
                delay(1000)
            }
        }
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
            pos = (pos + (diff.normalize() * speed.coerceAtMost(diffLen))).round()
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


public inline fun <T> Iterable<T>.sumByLong(selector: (T) -> Long): Long {
    var sum: Long = 0
    for (element in this) {
        sum += selector(element)
    }
    return sum
}

//bots: 10
//rooms: 1
//duration: 60s
//sent packets: 2918 (48 packets/s)
//received packets: 5985 (99 packets/s)
//total bytes sent: 124985 (42 bytes/packet)
//total bytes received: 905011 (151 bytes/packet)
//vertx bytes read: 901120.0
//latency measurements: 2917
//pending latency measurements: 1
//0.5 percentile - 78.6432 ms
//0.95 percentile - 124.780544 ms
//0.99 percentile - 133.169152 ms
//0.999 percentile - 158.334976 ms

//bots: 10
//rooms: 1
//duration: 60s
//sent packets: 2897 (48 packets/s)
//received packets: 5485 (91 packets/s)
//total bytes sent: 124061 (42 bytes/packet)
//total bytes received: 872689 (159 bytes/packet)
//vertx bytes read: 860160.0
//latency measurements: 2887
//pending latency measurements: 10
//0.5 percentile - 78.6432 ms
//0.95 percentile - 128.974848 ms
//0.99 percentile - 242.221056 ms
//0.999 percentile - 300.941312 ms


//bots: 300
//rooms: 10
//duration: 60s
//sent packets: 84910 (1415 packets/s)
//received packets: 163133 (2718 packets/s)
//total bytes sent: 3636489 (42 bytes/packet)
//total bytes received: 64094823 (392 bytes/packet)
//vertx bytes read: 6.3471616E7
//latency measurements: 83986
//pending latency measurements: 924
//0.5 percentile - 91.226112 ms
//0.95 percentile - 158.334976 ms
//0.99 percentile - 191.889408 ms
//0.999 percentile - 225.44384 ms

//bots: 300
//rooms: 10
//duration: 60s
//sent packets: 84335 (1405 packets/s)
//received packets: 153935 (2565 packets/s)
//total bytes sent: 3612358 (42 bytes/packet)
//total bytes received: 63053028 (409 bytes/packet)
//vertx bytes read: 6.2377984E7
//latency measurements: 82961
//pending latency measurements: 1374
//0.5 percentile - 108.003328 ms
//0.95 percentile - 569.376768 ms
//0.99 percentile - 1945.10848 ms
//0.999 percentile - 3354.394624 ms

//bots: 300
//rooms: 1
//duration: 60s
//sent packets: 83909 (1398 packets/s)
//received packets: 144892 (2414 packets/s)
//total bytes sent: 3593139 (42 bytes/packet)
//total bytes received: 350070652 (2416 bytes/packet)
//vertx bytes read: 3.49462528E8
//latency measurements: 49868
//pending latency measurements: 34028
//0.5 percentile - 5628.755968 ms
//0.95 percentile - 20392.706048 ms
//0.99 percentile - 26835.156992 ms
//0.999 percentile - 34351.34976 ms

//uuid = 16 byte = 128 bit = 22 x 6 bit char = 22 byte
