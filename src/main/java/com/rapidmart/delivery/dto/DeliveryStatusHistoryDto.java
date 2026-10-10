package com.rapidmart.delivery.dto;

import com.rapidmart.common.enums.DeliveryStatus;
import com.rapidmart.delivery.entity.DeliveryStatusHistory;

import java.time.OffsetDateTime;

public record DeliveryStatusHistoryDto(
        Long id,
        DeliveryStatus status,
        String statusMessage,
        String updatedBy,
        OffsetDateTime timestamp
) {
    public static DeliveryStatusHistoryDto from(DeliveryStatusHistory history) {
        return new DeliveryStatusHistoryDto(
                history.getId(),
                history.getStatus(),
                history.getStatusMessage(),
                history.getUpdatedBy(),
                history.getCreatedAt()
        );
    }
}
