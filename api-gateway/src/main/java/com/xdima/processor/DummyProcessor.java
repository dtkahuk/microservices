package com.xdima.processor;

import com.xdima.dto.CustomerDTO;
import com.xdima.dto.OwnerDTO;
import com.xdima.dto.PersonDTO;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DummyProcessor implements Processor {
    private static List<OwnerDTO> ownerDTOS = new ArrayList<>(Arrays.asList(
            new OwnerDTO(1L, "owner1", "0501641550"),
            new OwnerDTO(2L, "owner2", "0981762924")));
    private static List<CustomerDTO> customerDTOS = new ArrayList<>(Arrays.asList(
            new CustomerDTO(1L, "customer1", "1111111"),
            new CustomerDTO(2L, "customer2", "2222222")
    ));
    @Override
    public Flux<OwnerDTO> getOwners() {
        return Flux.fromIterable(ownerDTOS);
    }

    @Override
    public Flux<CustomerDTO> getCustomers() {
        return Flux.fromIterable(customerDTOS);
    }

    @Override
    public Flux<PersonDTO> getPersons() {
        return Flux.concat(Flux.fromIterable(ownerDTOS), Flux.fromIterable(customerDTOS));
    }
}
