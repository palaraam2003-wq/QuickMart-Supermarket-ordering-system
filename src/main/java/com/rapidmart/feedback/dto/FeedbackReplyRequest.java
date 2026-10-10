package com.rapidmart.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeedbackReplyRequest(
        @NotBlank(message = "Reply message is required")
        @Size(max = 5000, message = "Reply is too long")
        String replyMessage,

        @Size(max = 100, message = "Category is too long")
        String category
) {
}
