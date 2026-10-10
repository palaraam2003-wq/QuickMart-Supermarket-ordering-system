package com.rapidmart.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFeedbackRequest(
        @NotBlank(message = "Category is required")
        @Size(max = 100, message = "Category is too long")
        String category,

        @NotBlank(message = "Subject is required")
        @Size(max = 255, message = "Subject is too long")
        String subject,

        @NotBlank(message = "Message is required")
        @Size(max = 5000, message = "Message is too long")
        String message
) {
}
