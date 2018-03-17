package com.vvlasov.playground.rest.akkahttp;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.*;
import akka.http.javadsl.server.HttpApp;
import akka.http.javadsl.server.Route;
import akka.http.scaladsl.Http;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.vvlasov.playground.rest.Application;
import lombok.SneakyThrows;
import scala.concurrent.Future;

/**
 * @author Vasily Vlasov
 */
public class AkkaApplicationServer extends HttpApp implements Application {
    @Override
    @SneakyThrows
    public void start(String[] args) {
        ActorSystem system = ActorSystem.create();
        bindRoute("127.0.0.1",8080, system);
        System.out.println("Type RETURN to exit");
        System.in.read();
        system.shutdown();
    }

    @Override
    public Route createRoute() {
        return route(
                get(
                        pathEndOrSingleSlash().route(complete("Directory")),
                        path("ping").route(complete("PONG!")),
//                        path("crash", route(get(()-> {
//                            return complete("OK");
//                        }))),
                        path("stream").route(complete(streamingResponse()))
                )
        );
    }


    private HttpResponse streamingResponse() {
        // TODO javadsl getting much more streamlined in 1.1 (released soon)
        final Source<ByteString, Object> data = Source
                .repeat("Hello!")
                .take(5)
                .map(ByteString::fromString)
                .mapMaterializedValue(s -> null); // drop materialized value

        final ContentType ct = ContentType.create(MediaTypes.TEXT_PLAIN);
        final int len = 30; // TODO length not needed in 1.1
        final HttpEntityDefault chunked = HttpEntities.create(ct, len, data);
        return HttpResponse.create().withEntity(chunked);
    }
}
