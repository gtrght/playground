package com.vvlasov.playground.rest;

import com.vvlasov.playground.rest.akkahttp.AkkaApplicationServer;
import com.vvlasov.playground.rest.vertx.VertxApplicationServer;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * @author Vasily Vlasov
 */
@Slf4j
public class Launcher {
    public static void main(String[] args) {
        Application server;
        if (Arrays.stream(args).anyMatch(val -> val.equalsIgnoreCase("akka"))) {
            server = new AkkaApplicationServer();
        } else if (Arrays.stream(args).anyMatch(val -> val.equalsIgnoreCase("vertx"))) {
            server = new VertxApplicationServer();
        } else {
            server = new AkkaApplicationServer();
        }
        server.start(args);
    }
}
