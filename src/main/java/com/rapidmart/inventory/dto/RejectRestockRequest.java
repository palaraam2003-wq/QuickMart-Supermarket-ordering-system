package com.rapidmart.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RejectRestockRequest {

    @NotBlank(message = "A rejection reason is required")
    @Size(max = 500, message = "Rejection reason must be at most 500 characters")
    private String reason;

    public RejectRestockRequest() {
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
