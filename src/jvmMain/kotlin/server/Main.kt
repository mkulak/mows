package server

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.await

suspend fun main() {
    val vertx = Vertx.vertx()
    val mapper = createObjectMapper()
    val wsApi = WsApi(mapper)
    val gameService = GameService(wsApi)
    wsApi.gameService = gameService
    val httpApi = HttpApi(vertx)
    vertx.createHttpServer()
        .requestHandler(httpApi)
        .webSocketHandler(wsApi)
        .exceptionHandler { it.printStackTrace() }
        .listen(8080)
        .await()
    println("Started at :8080")
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


