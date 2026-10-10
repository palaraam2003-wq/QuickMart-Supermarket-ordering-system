package com.rapidmart.feedback.dto;

import com.rapidmart.common.enums.FeedbackStatus;
import com.rapidmart.feedback.entity.Feedback;
import com.rapidmart.feedback.entity.FeedbackReply;

import java.time.OffsetDateTime;
import java.util.List;

public record FeedbackDto(
        Long id,
        String customerName,
        String customerEmail,
        String category,
        String subject,
        String message,
        FeedbackStatus status,
        OffsetDateTime createdAt,
        List<ReplyDto> replies
) {
    public record ReplyDto(Long id, String repliedByName, String replyMessage, OffsetDateTime createdAt) {
        public static ReplyDto from(FeedbackReply reply) {
            return new ReplyDto(reply.getId(), reply.getRepliedBy().getFullName(), reply.getReplyMessage(), reply.getCreatedAt());
        }
    }

    public static FeedbackDto from(Feedback f, List<FeedbackReply> replies) {
        return new FeedbackDto(
                f.getId(),
                f.getUser().getFullName(),
                f.getUser().getEmail(),
                f.getCategory(),
                f.getSubject(),
                f.getMessage(),
                f.getStatus(),
                f.getCreatedAt(),
                replies.stream().map(ReplyDto::from).toList()
        );
    }
}
