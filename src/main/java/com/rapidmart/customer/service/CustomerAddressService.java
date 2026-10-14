package com.rapidmart.customer.service;

import com.rapidmart.common.exception.ResourceNotFoundException;
import com.rapidmart.customer.dto.AddressDto;
import com.rapidmart.customer.dto.CreateAddressRequest;
import com.rapidmart.customer.entity.CustomerAddress;
import com.rapidmart.customer.repository.CustomerAddressRepository;
import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CustomerAddressService {

    private final CustomerAddressRepository addressRepository;
    private final UserRepository userRepository;

    public CustomerAddressService(CustomerAddressRepository addressRepository, UserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    @Transactional(readOnly = true)
    public List<AddressDto> getMyAddresses() {
        User user = getAuthenticatedUser();
        return addressRepository.findByUserIdAndActiveTrue(user.getId())
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public AddressDto createAddress(CreateAddressRequest request) {
        User user = getAuthenticatedUser();

        if (request.isDefault()) {
            clearPreviousDefault(user.getId());
        }

        CustomerAddress address = new CustomerAddress(
                user,
                request.getStreetAddress().trim(),
                request.getCity().trim(),
                request.getPostalCode().trim(),
                request.getPhoneContact().trim(),
                request.isDefault(),
                true
        );

        CustomerAddress saved = addressRepository.save(address);
        return mapToDto(saved);
    }

    @Transactional
    public AddressDto updateAddress(Long addressId, CreateAddressRequest request) {
        User user = getAuthenticatedUser();

        CustomerAddress address = addressRepository.findByIdAndUserIdAndActiveTrue(addressId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("CustomerAddress", "id", addressId));

        if (request.isDefault() && !address.isDefault()) {
            clearPreviousDefault(user.getId());
        }

        address.setStreetAddress(request.getStreetAddress().trim());
        address.setCity(request.getCity().trim());
        address.setPostalCode(request.getPostalCode().trim());
        address.setPhoneContact(request.getPhoneContact().trim());
        address.setDefault(request.isDefault());

        CustomerAddress updated = addressRepository.save(address);
        return mapToDto(updated);
    }

    @Transactional
    public void deleteAddress(Long addressId) {
        User user = getAuthenticatedUser();

        CustomerAddress address = addressRepository.findByIdAndUserIdAndActiveTrue(addressId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("CustomerAddress", "id", addressId));

        // Soft delete to protect historical order snapshots
        address.setActive(false);
        addressRepository.save(address);
    }

    private void clearPreviousDefault(Long userId) {
        addressRepository.findByUserIdAndIsDefaultTrueAndActiveTrue(userId)
                .ifPresent(previousDefault -> {
                    previousDefault.setDefault(false);
                    addressRepository.save(previousDefault);
                });
    }

    public AddressDto mapToDto(CustomerAddress address) {
        return new AddressDto(
                address.getId(),
                address.getStreetAddress(),
                address.getCity(),
                address.getPostalCode(),
                address.getPhoneContact(),
                address.isDefault()
        );
    }
}
