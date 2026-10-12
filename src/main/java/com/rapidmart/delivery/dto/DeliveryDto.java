package com.rapidmart.delivery.dto;

import com.rapidmart.common.enums.DeliveryStatus;
import com.rapidmart.common.enums.OrderStatus;
import com.rapidmart.common.enums.PaymentMethod;
import com.rapidmart.common.enums.PaymentStatus;
import com.rapidmart.delivery.entity.Delivery;
import com.rapidmart.order.entity.Order;
import com.rapidmart.payment.entity.Payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DeliveryDto(
        Long id,
        Long orderId,
        String orderNumber,
        OrderStatus orderStatus,
        String deliveryStreet,
        String deliveryCity,
        String deliveryPostalCode,
        String deliveryPhone,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        DeliveryStatus deliveryStatus,
        LocalDate scheduledDate,
        String scheduledSlot,
        Long assignedStaffId,
        String assignedStaffName,
        String failureReason,
        boolean returnedItemsChecked,
        OffsetDateTime completedAt
) {
    public static DeliveryDto from(Delivery delivery, Payment payment) {
        Order order = delivery.getOrder();
        return new DeliveryDto(
                delivery.getId(),
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getDeliveryStreet(),
                order.getDeliveryCity(),
                order.getDeliveryPostalCode(),
                order.getDeliveryPhone(),
                order.getTotalAmount(),
                payment != null ? payment.getPaymentMethod() : null,
                payment != null ? payment.getPaymentStatus() : null,
                delivery.getDeliveryStatus(),
                delivery.getScheduledDate(),
                delivery.getScheduledSlot(),
                delivery.getAssignedStaff() != null ? delivery.getAssignedStaff().getId() : null,
                delivery.getAssignedStaff() != null ? delivery.getAssignedStaff().getFullName() : null,
                delivery.getFailureReason(),
                delivery.isReturnedItemsChecked(),
                delivery.getCompletedAt()
        );
    }
}
