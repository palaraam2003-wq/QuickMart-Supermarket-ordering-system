package com.rapidmart.delivery.repository;

import com.rapidmart.delivery.entity.CodSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodSettlementRepository extends JpaRepository<CodSettlement, Long> {

    Optional<CodSettlement> findByDeliveryId(Long deliveryId);

    List<CodSettlement> findByDeliveryStaffIdAndConfirmedByCashierFalse(Long staffId);

    List<CodSettlement> findByConfirmedByCashierFalse();

    List<CodSettlement> findByDeliveryStaffId(Long staffId);
}
