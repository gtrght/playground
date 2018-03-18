package com.vvlasov.playground.rest.akkahttp;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.HttpApp;
import akka.http.javadsl.server.Route;
import com.vvlasov.playground.rest.Application;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Vasily Vlasov
 */
@Slf4j
public class AkkaApplicationServer extends HttpApp implements Application {
    private static final HttpResponse UNSUPPORTED_METHOD_RESPONSE = HttpResponse.create()
            .withStatus(405)
            .withEntity("Unsupported method!");

    private static final HttpResponse NOT_FOUND_RESPONSE = HttpResponse.create()
            .withStatus(404)
            .withEntity("Unknown resource!");


    @Override
    @SneakyThrows
    public void start(String[] args) {
        final ActorSystem system = ActorSystem.create("routing-dsl");
        startServer("localhost", 8080, system);
    }

    /**
     * In the Java DSL, a Route can only consist of combinations of the built-in directives. A Route can not be instantiated directly.
     * However, the built-in directives may be combined methods like:
     *
     * @return route for a server
     * @see <a href="https://doc.akka.io/japi/akka-http/10.1.0/?akka/http/javadsl/server/Route.html">https://doc.akka.io/japi/akka-http/10.1.0/?akka/http/javadsl/server/Route.html</a>
     * <p>
     * Ok Boss! :)
     */
    @Override
    protected Route routes() {
        Route route1 = pathEndOrSingleSlash(() -> complete("Directory"));
        Route route2 = path("ping", () -> complete("pong!"));
        return route(
                get(() -> route1),
                get(() -> route2),
                post(() -> complete(UNSUPPORTED_METHOD_RESPONSE))
        );
    }
}
