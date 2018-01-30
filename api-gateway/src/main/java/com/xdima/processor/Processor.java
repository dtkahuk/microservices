package com.xdima.processor;

import com.xdima.dto.CustomerDTO;
import com.xdima.dto.OwnerDTO;
import com.xdima.dto.PersonDTO;
import reactor.core.publisher.Flux;

public interface Processor {
    Flux<OwnerDTO> getOwners ();
    Flux<CustomerDTO> getCustomers();
    Flux<PersonDTO> getPersons();
}
