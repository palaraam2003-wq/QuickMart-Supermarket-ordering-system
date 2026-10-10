package com.rapidmart.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 255, message = "Full name is too long")
        String fullName,

        @Pattern(regexp = "^$|^\\+?[0-9 ]{7,20}$", message = "Phone number is invalid")
        String phone
) {
}
