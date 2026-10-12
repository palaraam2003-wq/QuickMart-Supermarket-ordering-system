package com.rapidmart.delivery.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class SetDriverSlotLimitRequest {

    @NotNull(message = "Driver ID is required")
    private Long driverId;

    @NotNull(message = "Scheduled date is required")
    private LocalDate scheduledDate;

    @NotBlank(message = "Scheduled slot is required")
    private String scheduledSlot;

    @NotNull(message = "Maximum order limit is required")
    @Min(value = 0, message = "Maximum order limit must be at least 0")
    private Integer maxOrders;

    public SetDriverSlotLimitRequest() {
    }

    public SetDriverSlotLimitRequest(Long driverId, LocalDate scheduledDate, String scheduledSlot, Integer maxOrders) {
        this.driverId = driverId;
        this.scheduledDate = scheduledDate;
        this.scheduledSlot = scheduledSlot;
        this.maxOrders = maxOrders;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public String getScheduledSlot() {
        return scheduledSlot;
    }

    public void setScheduledSlot(String scheduledSlot) {
        this.scheduledSlot = scheduledSlot;
    }

    public Integer getMaxOrders() {
        return maxOrders;
    }

    public void setMaxOrders(Integer maxOrders) {
        this.maxOrders = maxOrders;
    }
}
