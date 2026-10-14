package com.rapidmart.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AddressDto {
    private Long id;
    private String streetAddress;
    private String city;
    private String postalCode;
    private String phoneContact;
    private boolean isDefault;

    public AddressDto() {
    }

    public AddressDto(Long id, String streetAddress, String city, String postalCode, String phoneContact, boolean isDefault) {
        this.id = id;
        this.streetAddress = streetAddress;
        this.city = city;
        this.postalCode = postalCode;
        this.phoneContact = phoneContact;
        this.isDefault = isDefault;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getPhoneContact() {
        return phoneContact;
    }

    public void setPhoneContact(String phoneContact) {
        this.phoneContact = phoneContact;
    }

    @JsonProperty("isDefault")
    public boolean isDefault() {
        return isDefault;
    }

    @JsonProperty("isDefault")
    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }
}
