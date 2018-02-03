package com.xdima;


import com.xdima.flux.TraceWebFilter;
import com.xdima.processor.GrpsProcessor;
import com.xdima.processor.Processor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.ipc.netty.http.server.HttpServer;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.RouterFunctions.toHttpHandler;

@SpringBootApplication
public class Server {
    private static final int PORT = 8080;
    private static final String API_SERVER = "api";

    public static void main(String[] args) {
        SpringApplication.run(Server.class, args);
    }

    private RouterFunction<ServerResponse> createRoute() {
//        Processor processor = new DummyProcessor();
        Processor processor = new GrpsProcessor();
        RequestHandler handler = new RequestHandler(processor);

        return route(GET("/persons").and(accept(MediaType.APPLICATION_JSON)), handler::getAllPeople)
                .andRoute(GET("/customers").and(accept(MediaType.APPLICATION_JSON)), handler::getCustomers)
                .andRoute(GET("/owners").and(accept(MediaType.APPLICATION_JSON)), handler::getOwners);
    }
    @Bean
    public String startServer(ApplicationContext ctx) {
        RouterFunction<ServerResponse> route = createRoute();
        HandlerStrategies.Builder builder = HandlerStrategies.builder();
        builder.webFilter(new TraceWebFilter(ctx, API_SERVER));
        HttpHandler httpHandler = toHttpHandler(route, builder.build());

        ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
        HttpServer server = HttpServer.create(PORT);
        //server.newHandler(adapter).block();
        server.startAndAwait(adapter);
        return "success";
    }
}
