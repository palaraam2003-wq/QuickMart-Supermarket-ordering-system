package com.rapidmart.delivery.dto;

import com.rapidmart.common.enums.DeliveryStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DelayedDeliveryDto(
        Long deliveryId,
        Long orderId,
        String orderNumber,
        Long assignedStaffId,
        String assignedStaffName,
        String assignedStaffEmail,
        LocalDate scheduledDate,
        String scheduledSlot,
        OffsetDateTime expectedCompletionTime,
        OffsetDateTime actualDeliveryTime,
        long delayDurationMinutes,
        String delayDurationFormatted,
        DeliveryStatus deliveryStatus,
        BigDecimal totalAmount,
        String customerName,
        String deliveryCity
) {
}
