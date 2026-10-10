package com.rapidmart.delivery.dto;

import com.rapidmart.delivery.entity.CodSettlement;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CodSettlementDto(
        Long id,
        Long deliveryId,
        Long orderId,
        String orderNumber,
        Long deliveryStaffId,
        String deliveryStaffName,
        String deliveryStaffEmail,
        BigDecimal collectedAmount,
        boolean confirmedByCashier,
        String cashierEmail,
        OffsetDateTime settledAt,
        OffsetDateTime createdAt
) {
    public static CodSettlementDto from(CodSettlement s) {
        return new CodSettlementDto(
                s.getId(),
                s.getDelivery().getId(),
                s.getDelivery().getOrder().getId(),
                s.getDelivery().getOrder().getOrderNumber(),
                s.getDeliveryStaff().getId(),
                s.getDeliveryStaff().getFullName(),
                s.getDeliveryStaff().getEmail(),
                s.getCollectedAmount(),
                s.isConfirmedByCashier(),
                s.getCashier() != null ? s.getCashier().getEmail() : null,
                s.getSettledAt(),
                s.getCreatedAt()
        );
    }
}
