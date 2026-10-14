package com.rapidmart.inventory.repository;

import com.rapidmart.common.enums.StockMovementType;
import com.rapidmart.inventory.entity.StockMovementHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMovementHistoryRepository extends JpaRepository<StockMovementHistory, Long> {

    Page<StockMovementHistory> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    Page<StockMovementHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByReferenceIdAndMovementType(String referenceId, StockMovementType movementType);
}
