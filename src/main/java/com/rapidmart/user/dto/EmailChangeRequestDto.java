package com.rapidmart.user.dto;

import com.rapidmart.common.enums.EmailChangeStatus;
import com.rapidmart.user.entity.EmailChangeRequest;

import java.time.OffsetDateTime;

public record EmailChangeRequestDto(Long id, String newEmail, EmailChangeStatus status, OffsetDateTime requestedAt) {

    public static EmailChangeRequestDto from(EmailChangeRequest request) {
        return new EmailChangeRequestDto(request.getId(), request.getNewEmail(), request.getStatus(), request.getCreatedAt());
    }
}
