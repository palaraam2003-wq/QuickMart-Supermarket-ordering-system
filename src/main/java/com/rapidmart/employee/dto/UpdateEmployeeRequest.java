package com.rapidmart.employee.dto;

import com.rapidmart.common.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UpdateEmployeeRequest(
        @NotBlank(message = "Full name is required")
        String fullName,

        String phone,

        @NotEmpty(message = "At least one role is required")
        Set<Role> roles,

        @NotNull(message = "Active flag is required")
        Boolean active
) {
}
