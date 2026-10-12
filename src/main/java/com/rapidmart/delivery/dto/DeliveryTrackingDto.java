package com.rapidmart.delivery.dto;

import com.rapidmart.common.enums.DeliveryStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public class DeliveryTrackingDto {

    private Long orderId;
    private String orderNumber;
    private Long deliveryId;
    private DeliveryStatus currentStatus;
    private LocalDate scheduledDeliveryDate;
    private String scheduledSlot;
    private String driverName;
    private String driverPhone;
    private String deliveryAddress;
    private String failureReason;
    private OffsetDateTime lastUpdated;
    private OffsetDateTime completedAt;
    private List<DeliveryStatusHistoryDto> history;

    public DeliveryTrackingDto() {
    }

    public DeliveryTrackingDto(Long orderId, String orderNumber, Long deliveryId, DeliveryStatus currentStatus,
                               LocalDate scheduledDeliveryDate, String scheduledSlot, String driverName,
                               String driverPhone, String deliveryAddress, String failureReason,
                               OffsetDateTime lastUpdated, OffsetDateTime completedAt,
                               List<DeliveryStatusHistoryDto> history) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.deliveryId = deliveryId;
        this.currentStatus = currentStatus;
        this.scheduledDeliveryDate = scheduledDeliveryDate;
        this.scheduledSlot = scheduledSlot;
        this.driverName = driverName;
        this.driverPhone = driverPhone;
        this.deliveryAddress = deliveryAddress;
        this.failureReason = failureReason;
        this.lastUpdated = lastUpdated;
        this.completedAt = completedAt;
        this.history = history;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Long getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(Long deliveryId) {
        this.deliveryId = deliveryId;
    }

    public DeliveryStatus getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(DeliveryStatus currentStatus) {
        this.currentStatus = currentStatus;
    }

    public LocalDate getScheduledDeliveryDate() {
        return scheduledDeliveryDate;
    }

    public void setScheduledDeliveryDate(LocalDate scheduledDeliveryDate) {
        this.scheduledDeliveryDate = scheduledDeliveryDate;
    }

    public String getScheduledSlot() {
        return scheduledSlot;
    }

    public void setScheduledSlot(String scheduledSlot) {
        this.scheduledSlot = scheduledSlot;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getDriverPhone() {
        return driverPhone;
    }

    public void setDriverPhone(String driverPhone) {
        this.driverPhone = driverPhone;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public OffsetDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(OffsetDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public List<DeliveryStatusHistoryDto> getHistory() {
        return history;
    }

    public void setHistory(List<DeliveryStatusHistoryDto> history) {
        this.history = history;
    }
}
