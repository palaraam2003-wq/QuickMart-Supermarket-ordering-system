package com.rapidmart.inventory.repository;

import com.rapidmart.common.enums.RestockRequestStatus;
import com.rapidmart.inventory.entity.RestockRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RestockRequestRepository extends JpaRepository<RestockRequest, Long> {

    List<RestockRequest> findByStatus(RestockRequestStatus status);

    Page<RestockRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<RestockRequest> findByStatusOrderByCreatedAtDesc(RestockRequestStatus status, Pageable pageable);

    boolean existsByProductIdAndStatusIn(Long productId, Collection<RestockRequestStatus> statuses);

    /** Row lock held until commit: serialises approve/reject/receive on the same request. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RestockRequest r WHERE r.id = :id")
    Optional<RestockRequest> findByIdForUpdate(@Param("id") Long id);
}
