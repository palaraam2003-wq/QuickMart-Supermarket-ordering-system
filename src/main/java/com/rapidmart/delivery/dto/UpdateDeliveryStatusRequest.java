package com.rapidmart.delivery.dto;

import com.rapidmart.common.enums.DeliveryStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateDeliveryStatusRequest {

    @NotNull(message = "Status is required")
    private DeliveryStatus status;

    private String message;

    public UpdateDeliveryStatusRequest() {
    }

    public UpdateDeliveryStatusRequest(DeliveryStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(DeliveryStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
