package com.rapidmart.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailChangeRequestBody(
        @NotBlank(message = "New email is required")
        @Email(message = "Invalid email format")
        @Size(max = 255, message = "Email is too long")
        String newEmail
) {
}
