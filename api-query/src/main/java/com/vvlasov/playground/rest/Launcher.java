package com.vvlasov.playground.rest;

import com.vvlasov.playground.rest.akkahttp.AkkaApplicationServer;
import com.vvlasov.playground.rest.vertx.VertxApplicationServer;

/**
 * @author Vasily Vlasov
 */
public class Launcher {
    public static void main(String[] args) {
        new AkkaApplicationServer().start(args);
    }
}
