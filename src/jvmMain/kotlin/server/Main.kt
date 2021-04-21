package server

import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import mu.KotlinLogging

val logger = KotlinLogging.logger {}

val json = Json { ignoreUnknownKeys = true }

suspend fun main() {
    val port = System.getenv("WONDER_PORT")?.toInt() ?: 8080
    val vertx = Vertx.vertx().exceptionHandler {
        logger.error("Uncaught exception: $it")
    }
    val wsApi = WsApi(json)
    val gameService = GameService(wsApi)
    wsApi.gameService = gameService
    val httpApi = HttpApi(vertx)
    val scope = CoroutineScope(vertx.dispatcher())
    val ticker = RoomTicker(scope, gameService)
    ticker.start()
    vertx.createHttpServer()
        .requestHandler(httpApi)
        .webSocketHandler(wsApi)
        .exceptionHandler { it.printStackTrace() }
        .listen(port)
        .await()
    println("space-poc-server v1 started at :$port")
}
