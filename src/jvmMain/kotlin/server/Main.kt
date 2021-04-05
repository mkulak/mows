package server

import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.await
import kotlinx.serialization.json.Json
import mu.KotlinLogging

val logger = KotlinLogging.logger {}

val json = Json { ignoreUnknownKeys = true }

suspend fun main() {
    val vertx = Vertx.vertx()
    val wsApi = WsApi(json)
    val gameService = GameService(wsApi)
    wsApi.gameService = gameService
    val httpApi = HttpApi(vertx)
    logger.info("Starting server")
    vertx.createHttpServer()
        .requestHandler(httpApi)
        .webSocketHandler(wsApi)
        .exceptionHandler { it.printStackTrace() }
        .listen(8080)
        .await()
    println("Started at :8080")
}
