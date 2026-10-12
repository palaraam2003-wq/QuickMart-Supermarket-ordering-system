package com.rapidmart.delivery.repository;

import com.rapidmart.delivery.entity.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, Long> {

    List<DeliveryAttempt> findByDeliveryIdOrderByAttemptTimestampDesc(Long deliveryId);
}
