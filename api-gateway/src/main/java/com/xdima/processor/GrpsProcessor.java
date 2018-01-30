package com.xdima.processor;

import brave.Tracing;
import brave.grpc.GrpcTracing;
import com.xdima.client.CustomerClient;
import com.xdima.client.OwnerClient;
import com.xdima.dto.CustomerDTO;
import com.xdima.dto.OwnerDTO;
import com.xdima.dto.PersonDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.okhttp3.OkHttpSender;

public class GrpsProcessor implements Processor {
    private static final String OWNER_SERVER = "owner";
    private static final String CUSTOMER_SERVER = "customer";
    private static final String LOCAL_SERVER = "localhost";
    private static final int OWNER_PORT = 50051;
    private static final int CUSTOMER_PORT = 50052;
    private static final int ZIPKIN_PORT = 9411;
    private static final String ZIPKIN_SERVER = "zipkin";

    private final OwnerClient ownerClient;
    private final CustomerClient customerClient;

    public GrpsProcessor() {
        OkHttpSender okHttpSender = OkHttpSender.create("http://127.0.0.1:9411/api/v2/spans");
        AsyncReporter<Span> reporter = AsyncReporter.create(okHttpSender);
        Tracing tracing = Tracing.newBuilder()
                .localServiceName(CUSTOMER_SERVER)
//                .localEndpoint(Endpoint.newBuilder().ip(ZIPKIN_SERVER).port(ZIPKIN_PORT).build())
                .spanReporter(reporter)
                .build();
        GrpcTracing grpcTracing = GrpcTracing.create(tracing);
        ownerClient = new OwnerClient(OWNER_SERVER, OWNER_PORT);
        customerClient = new CustomerClient(CUSTOMER_SERVER, CUSTOMER_PORT, grpcTracing);
    }

    @Override
    public Flux<OwnerDTO> getOwners() {
        return ownerClient.getOwners();
    }

    @Override
    public Flux<CustomerDTO> getCustomers() {
        return customerClient.getCustomers();
    }

    @Override
    public Flux<PersonDTO> getPersons() {
//        Flux<PersonDTO> ownerPersons = getOwners().map(ownerDTO -> new PersonDTO(ownerDTO.getId(), ownerDTO.getName()));
//        Flux<PersonDTO> customerPersons = getCustomers().map(customerDTO -> new PersonDTO(customerDTO.getId(), customerDTO.getName()));
//        return ownerPersons.concatWith(customerPersons);
        Flux<PersonDTO> customers = getCustomers().flatMap(customerDTO -> Mono.defer(() -> Mono.just(new PersonDTO(customerDTO.getId(), customerDTO.getName()))));
        Flux<PersonDTO> owners = getOwners().flatMap(ownerDTO -> Mono.defer(() -> Mono.just(new PersonDTO(ownerDTO.getId(), ownerDTO.getName()))));
        return owners.mergeWith(customers);
    }
}
