package com.rapidmart.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateDeliveryStaffRequest(
        @NotBlank(message = "Full name is required")
        String fullName,

        String phone,

        @NotNull(message = "Active flag is required")
        Boolean active
) {
}
