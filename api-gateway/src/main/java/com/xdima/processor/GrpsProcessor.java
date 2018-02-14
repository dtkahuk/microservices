package com.xdima.processor;

import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

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
    private static final String KAFKA_TOPIC = "microservices";


    private final OwnerClient ownerClient;
    private final CustomerClient customerClient;
    private final KafkaProducer<String, String> kafkaProducer;

    public GrpsProcessor() {
        //Configure the Producer
        Properties configProperties = new Properties();
        configProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,String.format(ZipkinUtils.KAFKA_ADDRESS, ZipkinUtils.KAFKA_SERVER, ZipkinUtils.KAFKA_PORT));
        configProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.ByteArraySerializer");
        configProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.StringSerializer");
        kafkaProducer = new KafkaProducer<>(configProperties);

        Tracing ownerTracing = ZipkinUtils.createTracing(ZipkinUtils.createSender(), OWNER_SERVER);
        ownerClient = new OwnerClient(OWNER_SERVER, OWNER_PORT, GrpcTracing.create(ownerTracing));

        Tracing customerTracing = ZipkinUtils.createTracing(ZipkinUtils.createSender(), CUSTOMER_SERVER);
        customerClient = new CustomerClient(CUSTOMER_SERVER, CUSTOMER_PORT, GrpcTracing.create(customerTracing));
    }

    @Override
    public Flux<OwnerDTO> getOwners() {
        ProducerRecord<String, String> record = new ProducerRecord<>(KAFKA_TOPIC, OWNER_SERVER);
        kafkaProducer.send(record);
        return ownerClient.getOwners();
    }

    @Override
    public Flux<CustomerDTO> getCustomers() {
        ProducerRecord<String, String> record = new ProducerRecord<>(KAFKA_TOPIC, CUSTOMER_SERVER);
        kafkaProducer.send(record);
        return customerClient.getCustomers();
    }

    @Override
    public Flux<PersonDTO> getPersons() {
        Flux<PersonDTO> customers = getCustomers().flatMap(customerDTO -> Mono.defer(() -> Mono.just(new PersonDTO(customerDTO.getId(), customerDTO.getName()))));
        Flux<PersonDTO> owners = getOwners().flatMap(ownerDTO -> Mono.defer(() -> Mono.just(new PersonDTO(ownerDTO.getId(), ownerDTO.getName()))));
        return owners.mergeWith(customers);
    }
}
