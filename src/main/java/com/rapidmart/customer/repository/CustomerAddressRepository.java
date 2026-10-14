package com.rapidmart.customer.repository;

import com.rapidmart.customer.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByUserIdAndActiveTrue(Long userId);

    Optional<CustomerAddress> findByIdAndUserIdAndActiveTrue(Long id, Long userId);

    Optional<CustomerAddress> findByUserIdAndIsDefaultTrueAndActiveTrue(Long userId);
}
