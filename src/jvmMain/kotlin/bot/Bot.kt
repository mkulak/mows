import common.ClientCommand
import common.FullRoomUpdateMessage
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
import io.vertx.micrometer.MicrometerMetricsOptions
import kotlinx.coroutines.CancellationException
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
import server.logger
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.coroutines.coroutineContext
import kotlin.random.Random.Default.nextDouble
import kotlin.random.Random.Default.nextLong
import kotlin.time.ExperimentalTime

val json = Json { ignoreUnknownKeys = true }
val speed = 100.0
val registry = SimpleMeterRegistry()
val myPositionTimer = timer("my-position-latency")
val roomUpdateTimer = timer("room-update-latency")
val loginTimer = timer("login-latency")

val vertx = Vertx.vertx().exceptionHandler {
    println("Uncaught exception: $it")
}
val hostAndPort = System.getenv("TARGET_URL") ?: "ec2-18-156-174-230.eu-central-1.compute.amazonaws.com:7000"

@ExperimentalTime
suspend fun main(args: Array<String>) {
    val scope = CoroutineScope(Dispatchers.Default)
    if (args.size != 3) {
        println("required args: bots-count rooms-count duration-in-seconds")
        System.exit(0)
    }
    val botCount = args[0].toInt()
    val rooms = args[1].toInt()
    val duration = args[2].toInt()
    println("target: $hostAndPort")
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
    scope.coroutineContext[Job]?.cancelChildren(StopTest())
    println("shutting down")
    tasks.forEach { it.join() }
    vertx.close()
    val deadBotsCount = bots.count { it.quitAbruptly }
    if (deadBotsCount != 0) {
        println("WARNING: $deadBotsCount bots died. Results are affected")
    }
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
    println("latency measurements: $latencyMeasured")
    println("pending latency measurements: $pendingLatency")
    loginTimer.printSummery()
    myPositionTimer.printSummery()
    roomUpdateTimer.printSummery()
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

    var startedAt = 0L
    var receivedFullUpdate = false
    var lastRoomUpdateAt = 0L
    var quitAbruptly = false

    suspend fun start() {
        try {
            startedAt = System.currentTimeMillis()
            val ws = connect()
            delay(nextLong(1000))
            while (coroutineContext.isActive && !quitAbruptly) {
                if (!receivedFullUpdate) {
                    delay(500)
                    continue
                }
                if (walking) {
                    advancePos()
                    sendPos(ws, pos)
                    delay(300)
                } else {
                    delay(1000)
                }
            }
        } catch (ignore: StopTest) {
        } catch (e: Exception) {
            handleException(e)
        }
    }

    private fun handleException(e: Throwable) {
        logger.error("Bot #${bot} from room ${room} got error: $e")
        quitAbruptly = true
    }

    private suspend fun connect(): WebSocket =
        awaitResult { handler ->
            val (host, port) = hostAndPort.split(":")
            val options = WebSocketConnectOptions()
                .setSsl(false)
//            .setHost("wonder.kvarto.net")
                .setHost(host)
//                .setHost("localhost")
//                .setPort(8080)
                .setPort(port.toInt())
                .setURI("/rooms/$room")
            vertx.createHttpClient().webSocket(options) {
                it.result()?.textMessageHandler(::handleMessage)?.exceptionHandler(::handleException)
                handler.handle(it)
            }
        }

    private fun handleMessage(res: String) {
        receivedCount++
        receivedSize += res.length
        val msg = json.decodeFromString<ServerMessage>(res)
        val now = System.currentTimeMillis()
        when (msg) {
            is LoginMessage -> myId = msg.id
            is FullRoomUpdateMessage -> {
                receivedFullUpdate = true
                loginTimer.record(System.currentTimeMillis() - startedAt, MILLISECONDS)
            }
            is UpdateMessage -> {
                val index = msg.ids.indexOf(myId)
                if (index != -1) {
                    val pos = XY(msg.xs[index], msg.ys[index])
                    val sentAt = pos2time.remove(pos)
                    if (sentAt != null) {
                        myPositionTimer.record(now - sentAt, MILLISECONDS)
                        latencyMeasured++
                    }
                }
                if (lastRoomUpdateAt != 0L) {
                    roomUpdateTimer.record(now - lastRoomUpdateAt, MILLISECONDS)
                }
                lastRoomUpdateAt = now
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

inline fun <T> Iterable<T>.sumByLong(selector: (T) -> Long): Long {
    var sum: Long = 0
    for (element in this) {
        sum += selector(element)
    }
    return sum
}

fun timer(name: String, desc: String = ""): Timer =
    Timer.builder(name)
        .description(desc)
        .publishPercentiles(0.5, 0.95, 0.99, 0.999)
        .publishPercentileHistogram()
        .register(registry)


fun Timer.printSummery() {
    println("${id.name}:")
    takeSnapshot().percentileValues().forEach {
        println("${it.percentile()} percentile - ${it.value().toLong() / 1000000} ms")
    }
}

class StopTest : CancellationException()
