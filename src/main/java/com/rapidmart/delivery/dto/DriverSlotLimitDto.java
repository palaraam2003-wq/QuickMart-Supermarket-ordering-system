package com.rapidmart.delivery.dto;

import com.rapidmart.delivery.entity.DriverSlotLimit;

import java.time.LocalDate;

public record DriverSlotLimitDto(
        Long id,
        Long driverId,
        String driverName,
        String driverEmail,
        LocalDate scheduledDate,
        String scheduledSlot,
        Integer maxOrders,
        long assignedCount,
        long remainingCapacity
) {
    public static DriverSlotLimitDto from(DriverSlotLimit limit, long assignedCount) {
        long capacity = Math.max(0, limit.getMaxOrders() - assignedCount);
        return new DriverSlotLimitDto(
                limit.getId(),
                limit.getDriver().getId(),
                limit.getDriver().getFullName(),
                limit.getDriver().getEmail(),
                limit.getScheduledDate(),
                limit.getScheduledSlot(),
                limit.getMaxOrders(),
                assignedCount,
                capacity
        );
    }
}
