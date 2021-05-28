import common.ClientCommand
import common.FullRoomUpdateMessage
import common.MoveCommand
import common.PingCommand
import common.PongCommand
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
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.WebSocket
import io.vertx.core.http.WebSocketConnectOptions
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.awaitResult
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.coroutines.coroutineContext
import kotlin.random.Random.Default.nextDouble
import kotlin.random.Random.Default.nextInt
import kotlin.random.Random.Default.nextLong
import kotlin.time.ExperimentalTime

val json = Json { ignoreUnknownKeys = true }
val speed = 100.0
val registry = SimpleMeterRegistry()
val roomUpdateTimer = timer("room-update-latency", "time between consecutive room updates")
val pingTimer = timer("ping-latency", "time between ping and pong")

val vertx = Vertx.vertx().exceptionHandler {
    println("Uncaught exception: $it")
}
val hostAndPort = System.getenv("TARGET_URL") ?: "ec2-18-156-174-230.eu-central-1.compute.amazonaws.com:7000"
val scope = CoroutineScope(vertx.dispatcher())

@ExperimentalTime
suspend fun main(args: Array<String>) {
    if (args.size != 3) {
        println("required args: bots-count rooms-count duration-in-seconds")
        System.exit(0)
    }
    val botCount = args[0].toInt()
    val rooms = args[1].toInt()
    val duration = args[2].toInt()
    println("bots v1.5, target: $hostAndPort")
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
    println("retrieving metrics")
//    val metrics = retrieveMetrics()
    val metrics = ""
    scope.coroutineContext[Job]?.cancelChildren(StopTest())
    println("shutting down")
    tasks.forEach { it.join() }
    vertx.close()
    val deadBotsCount = bots.count { it.quitAbruptly }
    if (deadBotsCount != 0) {
        println("WARNING: $deadBotsCount bots died. Results are affected")
    }
    printResults(bots, rooms, duration, metrics)
}

suspend fun retrieveMetrics(): String {
    val httpClient = WebClient.create(vertx)
    val (host, _) = hostAndPort.split(":")
    val raw = httpClient.get(3001, host, "/metrics").send().await().bodyAsString()
    return raw.split("\n").filter { "tick_duration{quantile=\"0.9" in it }.joinToString("\n")
}

fun printResults(bots: List<Bot>, rooms: Int, duration: Int, metrics: String) {
    val sentCount = bots.sumOf { it.sentCount }.coerceAtLeast(1)
    val sentSize = bots.sumOf { it.sentSize }
    val receivedCount = bots.sumOf { it.receivedCount }.coerceAtLeast(1)
    val receivedSize = bots.sumOf { it.receivedSize }
    println("bots: ${bots.size}")
    println("rooms: $rooms")
    println("duration: ${duration}s")
    println("sent packets: $sentCount (${sentCount / duration} packets/s)")
    println("received packets: $receivedCount (${receivedCount / duration} packets/s)")
    println("total bytes sent: $sentSize (${sentSize / sentCount} bytes/packet)")
    println("total bytes received: $receivedSize (${receivedSize / receivedCount} bytes/packet)")
    roomUpdateTimer.printSummery()
    pingTimer.printSummery()
    println(metrics)
}

class Bot(val vertx: Vertx, val bot: Int, val room: Int, val walking: Boolean) {
    var pos = randomPoint()
    var target = randomPoint()

    var sentCount = 0
    var sentSize = 0L
    var receivedCount = 0
    var receivedSize = 0L

    var startedAt = 0L
    var receivedFullUpdate = false
    var lastRoomUpdateAt = 0L
    var quitAbruptly = false
    val ping2time = HashMap<Long, Long>()
    var lastPingSentAt = 0L

    suspend fun start() {
        try {
            startedAt = System.currentTimeMillis()
            delay(nextLong(3000))
            val ws = connect()
            while (coroutineContext.isActive && !quitAbruptly) {
                if (receivedFullUpdate) {
                    trySendPing(ws)
                    if (walking) {
                        advancePos()
                        sendCommand(ws, MoveCommand(pos))
                    }
                }
                delay(300)
            }
        } catch (ignore: StopTest) {
        } catch (e: Exception) {
            handleException(e)
        }
    }

    fun trySendPing(ws: WebSocket) {
        val now = System.currentTimeMillis()
        if (now - lastPingSentAt < 1000) {
            return
        }
        val pingId = nextInt(100_000_000).toLong()
        ping2time[pingId] = now
        sendCommand(ws, PingCommand(pingId))
    }

    fun handleException(e: Throwable) {
        logger.error("Bot #${bot} from room ${room} got error: $e")
        quitAbruptly = true
    }

    suspend fun connect(): WebSocket =
        awaitResult { handler ->
            val (host, port) = hostAndPort.split(":")
            val options = WebSocketConnectOptions()
                .setSsl(false)
                .setHost(host)
                .setPort(port.toInt())
                .setURI("/rooms/$room")
            val httpOptions = HttpClientOptions().setMaxWebSocketFrameSize(64_000_000)
            vertx.createHttpClient(httpOptions).webSocket(options) {
                it.result()?.textMessageHandler(::handleMessage)?.exceptionHandler(::handleException)
                handler.handle(it)
            }
        }

    fun handleMessage(res: String) {
        val time = System.currentTimeMillis()
        receivedCount++
        receivedSize += res.length
        val msg = json.decodeFromString<ServerMessage>(res)
        when (msg) {
            is FullRoomUpdateMessage -> {
                receivedFullUpdate = true
            }
            is UpdateMessage -> {
                if (lastRoomUpdateAt != 0L) {
                    roomUpdateTimer.record(time - lastRoomUpdateAt, MILLISECONDS)
                }
                lastRoomUpdateAt = time
            }
            is PongCommand -> {
                val sentAt = ping2time.remove(msg.id)
                if (sentAt != null) {
                    pingTimer.record(time - sentAt, MILLISECONDS)
                }  else {
                    logger.warn("#${bot} received strange ping: ${msg.id}")
                }
            }
        }
    }

    fun sendCommand(ws: WebSocket, command: ClientCommand) {
        val str = json.encodeToString(command)
        ws.writeTextMessage(str)
        sentCount++
        sentSize += str.length
    }

    fun randomPoint() = XY(nextDouble(MAP_WIDTH), nextDouble(MAP_HEIGHT)).round()

    tailrec fun advancePos() {
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
        .distributionStatisticExpiry(Duration.of(5, ChronoUnit.MINUTES))
        .register(registry)


fun Timer.printSummery() {
    println("${id.name}:")
    takeSnapshot().percentileValues().forEach {
        println("${it.percentile()} percentile - ${it.value().toLong() / 1000000} ms")
    }
}

class StopTest : CancellationException()
