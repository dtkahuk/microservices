package com.xdima.dto;

public class CustomerDTO extends PersonDTO {
    private String email;

    public CustomerDTO(long id, String name, String email) {
        super(id, name);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
}
