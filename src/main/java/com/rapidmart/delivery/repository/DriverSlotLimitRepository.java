package com.rapidmart.delivery.repository;

import com.rapidmart.delivery.entity.DriverSlotLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverSlotLimitRepository extends JpaRepository<DriverSlotLimit, Long> {

    Optional<DriverSlotLimit> findByDriverIdAndScheduledDateAndScheduledSlot(Long driverId, LocalDate scheduledDate, String scheduledSlot);

    List<DriverSlotLimit> findByScheduledDateAndScheduledSlot(LocalDate scheduledDate, String scheduledSlot);

    List<DriverSlotLimit> findByDriverId(Long driverId);

    List<DriverSlotLimit> findByScheduledDate(LocalDate scheduledDate);
}
