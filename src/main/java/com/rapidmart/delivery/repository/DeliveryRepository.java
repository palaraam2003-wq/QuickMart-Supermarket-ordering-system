package com.rapidmart.delivery.repository;

import com.rapidmart.common.enums.DeliveryStatus;
import com.rapidmart.delivery.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByOrderId(Long orderId);

    List<Delivery> findByAssignedStaffId(Long staffId);

    List<Delivery> findByAssignedStaffIdAndDeliveryStatus(Long staffId, DeliveryStatus status);

    boolean existsByAssignedStaffIdAndScheduledDateAndScheduledSlot(Long staffId, LocalDate scheduledDate, String scheduledSlot);

    List<Delivery> findByDeliveryStatus(DeliveryStatus status);

    boolean existsByAssignedStaffIdAndScheduledDateAndScheduledSlotAndIdNot(Long staffId, LocalDate scheduledDate, String scheduledSlot, Long id);

    List<Delivery> findAllByOrderByScheduledDateAscIdAsc();

    @Query("select count(d) from Delivery d where d.assignedStaff.id = :staffId and d.scheduledDate = :date "
            + "and upper(d.scheduledSlot) = upper(:slot) and d.deliveryStatus in :statuses "
            + "and (:excludeId is null or d.id <> :excludeId)")
    long countDriverSlotAssignments(@Param("staffId") Long staffId,
                                    @Param("date") LocalDate date,
                                    @Param("slot") String slot,
                                    @Param("statuses") Collection<DeliveryStatus> statuses,
                                    @Param("excludeId") Long excludeId);
}
