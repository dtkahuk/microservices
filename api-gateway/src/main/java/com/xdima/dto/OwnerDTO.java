package com.xdima.dto;

public class OwnerDTO extends PersonDTO {
    private String phone;

    public OwnerDTO(long id, String name, String phone) {
        super(id, name);
        this.phone = phone;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
