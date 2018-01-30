package com.xdima;


import com.xdima.processor.GrpsProcessor;
import com.xdima.processor.Processor;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.ipc.netty.http.server.HttpServer;

import java.io.IOException;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.RouterFunctions.toHttpHandler;

public class Server {
    private static int PORT = 8080;

    public static void main(String[] args) throws IOException {
        startServer();
/*
        System.out.println("Press ENTER to exit.");
        System.in.read();
*/
    }

    private static RouterFunction<ServerResponse> createRoute() {
//        Processor processor = new DummyProcessor();
        Processor processor = new GrpsProcessor();
        RequestHandler handler = new RequestHandler(processor);

        return route(GET("/persons").and(accept(MediaType.APPLICATION_JSON)), handler::getAllPeople)
                .andRoute(GET("/customers").and(accept(MediaType.APPLICATION_JSON)), handler::getCustomers)
                .andRoute(GET("/owners").and(accept(MediaType.APPLICATION_JSON)), handler::getOwners);
    }

    private static void startServer(){
        RouterFunction<ServerResponse> route = createRoute();
        HttpHandler httpHandler = toHttpHandler(route);

        ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
        HttpServer server = HttpServer.create(PORT);
        //server.newHandler(adapter).block();
        server.startAndAwait(adapter);
    }

}
