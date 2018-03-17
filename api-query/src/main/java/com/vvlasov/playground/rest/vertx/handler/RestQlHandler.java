package com.vvlasov.playground.rest.vertx.handler;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;
import restql.core.RestQL;
import restql.core.config.ClassConfigRepository;
import restql.core.response.QueryResponse;

import javax.inject.Singleton;
import java.nio.charset.Charset;

import static com.vvlasov.playground.rest.util.Constants.HTTP_HEADER_ALGORITHM;

/**
 * @author Vasily Vlasov
 */
@Slf4j
@Singleton
public class RestQlHandler implements Handler<RoutingContext> {
    private final RestQL driver;

    public RestQlHandler() {
        ClassConfigRepository config = new ClassConfigRepository();
        config.put("planets", "https://swapi.co/api/planets/:id");
        driver = new RestQL(config);
    }

    @Override
    public void handle(RoutingContext event) {
        HttpServerRequest request = event.request();
        log.info("Received RestQl request value={}", request.absoluteURI());


        request.bodyHandler(body -> {
            //something like this should be executed
            //"from planets with id = 1"
            QueryResponse response = driver.executeQuery(body.toString(Charset.defaultCharset()));

            event.response()
                    .setStatusCode(200)
                    .putHeader(HTTP_HEADER_ALGORITHM, "restql")
                    .end(response.toString());
        });
    }
}
