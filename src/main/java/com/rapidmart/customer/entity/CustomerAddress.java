package com.rapidmart.customer.entity;

import com.rapidmart.common.entity.BaseEntity;
import com.rapidmart.user.entity.User;
import jakarta.persistence.*;

@Entity
@Table(name = "customer_addresses")
public class CustomerAddress extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "street_address", nullable = false)
    private String streetAddress;

    @Column(nullable = false)
    private String city;

    @Column(name = "postal_code", nullable = false)
    private String postalCode;

    @Column(name = "phone_contact", nullable = false)
    private String phoneContact;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(nullable = false)
    private boolean active = true;

    public CustomerAddress() {
    }

    public CustomerAddress(User user, String streetAddress, String city, String postalCode, String phoneContact, boolean isDefault) {
        this(user, streetAddress, city, postalCode, phoneContact, isDefault, true);
    }

    public CustomerAddress(User user, String streetAddress, String city, String postalCode, String phoneContact, boolean isDefault, boolean active) {
        this.user = user;
        this.streetAddress = streetAddress;
        this.city = city;
        this.postalCode = postalCode;
        this.phoneContact = phoneContact;
        this.isDefault = isDefault;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
