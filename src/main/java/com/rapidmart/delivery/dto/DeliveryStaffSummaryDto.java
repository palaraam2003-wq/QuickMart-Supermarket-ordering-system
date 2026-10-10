package com.rapidmart.delivery.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DeliveryStaffSummaryDto(
        Long id,
        String email,
        String fullName,
        String phone,
        boolean active,
        boolean coordinatorManageable,
        boolean available,
        long activeAssignmentCount,
        long totalAssignmentCount,
        LocalDate availabilityDate,
        String availabilitySlot,
        OffsetDateTime createdAt
) {
}
