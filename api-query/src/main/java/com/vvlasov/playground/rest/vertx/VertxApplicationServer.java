package com.vvlasov.playground.rest.vertx;

import com.vvlasov.playground.rest.Application;
import com.vvlasov.playground.rest.vertx.handler.RestQlHandler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Vasily Vlasov
 */
@Slf4j
public class VertxApplicationServer implements Application {
    public void start(String[] args) {
        Vertx vertx = Vertx.vertx();
        int port = 8080;
        log.info("Starting vertex application on port={}", port);

        Router router = Router.router(vertx);
        router.route(HttpMethod.POST, "/restql")
                .handler(new RestQlHandler());

        HttpServer server = vertx.createHttpServer()
                .requestHandler(router::accept).listen(port);
    }
}
