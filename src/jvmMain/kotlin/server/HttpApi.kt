package server

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerRequest
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.StaticHandler

class HttpApi(vertx: Vertx) : Handler<HttpServerRequest> {
    val router = Router.router(vertx)

    init {
        router.get("/*").handler(StaticHandler.create("output").setCachingEnabled(true).setMaxAgeSeconds(0))
    }

    override fun handle(event: HttpServerRequest) {
        router.handle(event)
    }
}
