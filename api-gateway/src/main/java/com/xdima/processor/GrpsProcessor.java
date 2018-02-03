package com.xdima.processor;

import brave.Tracing;
import brave.grpc.GrpcTracing;
import com.xdima.ZipkinUtils;
import com.xdima.client.CustomerClient;
import com.xdima.client.OwnerClient;
import com.xdima.dto.CustomerDTO;
import com.xdima.dto.OwnerDTO;
import com.xdima.dto.PersonDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class GrpsProcessor implements Processor {
    private static final String OWNER_SERVER = "owner";
    private static final String CUSTOMER_SERVER = "customer";
    private static final String LOCAL_SERVER = "localhost";
    private static final int OWNER_PORT = 50051;
    private static final int CUSTOMER_PORT = 50052;


    private final OwnerClient ownerClient;
    private final CustomerClient customerClient;

    public GrpsProcessor() {
        Tracing customerTracing = ZipkinUtils.createTracing(ZipkinUtils.createHttpSender(), CUSTOMER_SERVER);
        ownerClient = new OwnerClient(LOCAL_SERVER, OWNER_PORT, GrpcTracing.create(customerTracing));
        Tracing ownerTracing = ZipkinUtils.createTracing(ZipkinUtils.createHttpSender(), OWNER_SERVER);
        customerClient = new CustomerClient(LOCAL_SERVER, CUSTOMER_PORT, GrpcTracing.create(ownerTracing));
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
        Flux<PersonDTO> customers = getCustomers().flatMap(customerDTO -> Mono.defer(() -> Mono.just(new PersonDTO(customerDTO.getId(), customerDTO.getName()))));
        Flux<PersonDTO> owners = getOwners().flatMap(ownerDTO -> Mono.defer(() -> Mono.just(new PersonDTO(ownerDTO.getId(), ownerDTO.getName()))));
        return owners.mergeWith(customers);
    }
}
