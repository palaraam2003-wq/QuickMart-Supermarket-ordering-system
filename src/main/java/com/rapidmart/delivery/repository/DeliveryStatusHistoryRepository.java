package com.rapidmart.delivery.repository;

import com.rapidmart.delivery.entity.DeliveryStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryStatusHistoryRepository extends JpaRepository<DeliveryStatusHistory, Long> {

    List<DeliveryStatusHistory> findByDeliveryIdOrderByCreatedAtAscIdAsc(Long deliveryId);

    List<DeliveryStatusHistory> findByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);
}
