package server

import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpServerOptions
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.micrometer.MicrometerMetricsOptions
import io.vertx.micrometer.VertxPrometheusOptions
import io.vertx.micrometer.backends.BackendRegistries
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import mu.KotlinLogging

val logger = KotlinLogging.logger {}

val json = Json { ignoreUnknownKeys = true }

suspend fun main() {
    val port = System.getenv("WONDER_PORT")?.toInt() ?: 8080
    val vertx = createVertx()
    val registry = BackendRegistries.getDefaultNow()
    val wsApi = WsApi(json)
    val gameService = GameService(wsApi, registry)
    wsApi.gameService = gameService
    val httpApi = HttpApi(vertx)
    val scope = CoroutineScope(vertx.dispatcher())
    val ticker = RoomTicker(scope, gameService, registry)
    ticker.start()
    vertx.createHttpServer(HttpServerOptions().setPerMessageWebSocketCompressionSupported(false))
        .requestHandler(httpApi)
        .webSocketHandler(wsApi)
        .exceptionHandler { it.printStackTrace() }
        .listen(port)
        .await()
    println("space-poc-server v1 started at :$port")
}

private fun createVertx(): Vertx {
    val options = VertxPrometheusOptions()
        .setEnabled(true)
        .setStartEmbeddedServer(true)
        .setEmbeddedServerOptions(HttpServerOptions().setPort(3001))
        .setEmbeddedServerEndpoint("/metrics")
    val vertxOptions = VertxOptions()
        .setMetricsOptions(MicrometerMetricsOptions().setPrometheusOptions(options).setEnabled(true))
    return Vertx.vertx(vertxOptions).exceptionHandler {
        logger.error("Uncaught exception: $it")
    }
}
