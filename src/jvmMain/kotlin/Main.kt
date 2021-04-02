import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.vertx.core.Vertx
import io.vertx.core.http.ServerWebSocket
import io.vertx.kotlin.coroutines.await
import mu.KLogging
import java.lang.Integer.toHexString
import kotlin.random.Random.Default.nextDouble
import kotlin.random.Random.Default.nextInt
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING as embed

suspend fun main() {
    val vertx = Vertx.vertx()
    val mapper = createObjectMapper()
    val wsApi = WsApi(mapper)
    vertx.createHttpServer()
//        .requestHandler(httpApi.createApi())
        .webSocketHandler(wsApi::handle)
        .exceptionHandler { it.printStackTrace() }
        .listen(8081)
        .await()
    println("Started at :8081")
}

fun createObjectMapper(): ObjectMapper =
    ObjectMapper()
        .registerModule(KotlinModule())
        .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .registerModule(JavaTimeModule())


class WsApi(val mapper: ObjectMapper) {
    private val clients = mutableMapOf<PlayerId, ConnectedClient>()

    fun handle(ws: ServerWebSocket) {
        ws.accept()
        val playerId = PlayerId(nextId())
        val client = ConnectedClient(playerId, ws, XY(nextDouble(500.0), nextDouble(500.0)))
        clients[playerId] = client
        logger.info("Joined $playerId on ${ws.path()}")
        ws.textMessageHandler { msg ->
            handleClientCommand(msg, playerId)
        }
        ws.closeHandler {
            logger.info("Disconnected $playerId")
            clients.remove(playerId)
        }
        client.send(LoginMessage(playerId.value))
        client.send(UpdateMessage(playerId.value, client.pos))
    }

    private fun handleClientCommand(msg: String, playerId: PlayerId) {
        logger.info("Got $msg from $playerId")
        val command = mapper.readValue<MoveCommand>(msg)
        val oldClient = clients[playerId]
        if (oldClient == null) {
            logger.warn("Unknown client $playerId")
            return
        }
        val newClient = oldClient.copy(pos = command.pos)
        val response = UpdateMessage(playerId.value, newClient.pos)
        clients[playerId] = newClient
        clients.values.forEach {
            it.send(response)
        }
    }

    fun ConnectedClient.send(message: ServerMessage) {
        ws.writeTextMessage(mapper.writeValueAsString(message))
    }

    companion object : KLogging()
}

data class ConnectedClient(
    val id: PlayerId,
    val ws: ServerWebSocket,
    val pos: XY
)

data class PlayerId @JsonCreator(mode = embed) constructor(@JsonValue val value: String) {
    override fun toString() = value
}

fun nextId(): String = toHexString(nextInt()).toString()

