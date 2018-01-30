package com.xdima;

import com.xdima.dto.CustomerDTO;
import com.xdima.dto.OwnerDTO;
import com.xdima.dto.PersonDTO;
import com.xdima.processor.Processor;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.springframework.http.MediaType.APPLICATION_JSON;

public class RequestHandler {
    private Processor processor;

    public RequestHandler(Processor processor) {
        this.processor = processor;
    }

    public Mono<ServerResponse> getCustomers(ServerRequest serverRequest) {
        Flux<CustomerDTO> customers = processor.getCustomers();
        return ServerResponse.ok().contentType(APPLICATION_JSON).body(customers, CustomerDTO.class);
    }

    public Mono<ServerResponse> getOwners(ServerRequest serverRequest) {
        Flux<OwnerDTO> owners = processor.getOwners();
        return ServerResponse.ok().contentType(APPLICATION_JSON).body(owners, OwnerDTO.class);
    }

    public Mono<ServerResponse> getAllPeople(ServerRequest serverRequest) {
        Flux<PersonDTO> persons = processor.getPersons();
        return ServerResponse.ok().contentType(APPLICATION_JSON).body(persons, PersonDTO.class);
    }

}

