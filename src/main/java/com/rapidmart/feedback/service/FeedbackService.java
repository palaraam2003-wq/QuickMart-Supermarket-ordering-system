package com.rapidmart.feedback.service;

import com.rapidmart.common.enums.FeedbackStatus;
import com.rapidmart.common.exception.BusinessException;
import com.rapidmart.common.exception.ResourceNotFoundException;
import com.rapidmart.feedback.dto.FeedbackDto;
import com.rapidmart.feedback.entity.Feedback;
import com.rapidmart.feedback.entity.FeedbackReply;
import com.rapidmart.feedback.repository.FeedbackReplyRepository;
import com.rapidmart.feedback.repository.FeedbackRepository;
import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final FeedbackReplyRepository feedbackReplyRepository;
    private final UserRepository userRepository;

    public FeedbackService(FeedbackRepository feedbackRepository,
                           FeedbackReplyRepository feedbackReplyRepository,
                           UserRepository userRepository) {
        this.feedbackRepository = feedbackRepository;
        this.feedbackReplyRepository = feedbackReplyRepository;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private FeedbackDto toDto(Feedback feedback) {
        return FeedbackDto.from(feedback, feedbackReplyRepository.findByFeedbackIdOrderByCreatedAtAsc(feedback.getId()));
    }

    @Transactional
    public FeedbackDto submitFeedback(String category, String subject, String message) {
        User user = getAuthenticatedUser();
        Feedback feedback = new Feedback(user, category.trim(), subject.trim(), message.trim());
        return toDto(feedbackRepository.save(feedback));
    }

    @Transactional(readOnly = true)
    public List<FeedbackDto> getMyFeedback() {
        User user = getAuthenticatedUser();
        return feedbackRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Page<FeedbackDto> getAllFeedback(Pageable pageable) {
        return feedbackRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDto);
    }

    /** PENDING/RESPONDED -> RESPONDED. Closed feedback cannot receive new replies. */
    @Transactional
    public FeedbackDto replyToFeedback(Long feedbackId, String replyMessage, String category) {
        User manager = getAuthenticatedUser();
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback", "id", feedbackId));

        if (feedback.getStatus() == FeedbackStatus.CLOSED) {
            throw new BusinessException("FEEDBACK_CLOSED", "Closed feedback cannot receive new replies.");
        }
        if (replyMessage == null || replyMessage.isBlank()) {
            throw new BusinessException("REPLY_REQUIRED", "Reply message is required.");
        }
        if (category != null && !category.isBlank()) {
            feedback.setCategory(category.trim());
        }

        feedbackReplyRepository.save(new FeedbackReply(feedback, manager, replyMessage.trim()));
        feedback.setStatus(FeedbackStatus.RESPONDED);
        return toDto(feedbackRepository.save(feedback));
    }

    /** Any status -> CLOSED. Closing twice is a no-op. */
    @Transactional
    public FeedbackDto closeFeedback(Long feedbackId) {
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback", "id", feedbackId));

        if (feedback.getStatus() != FeedbackStatus.CLOSED) {
            feedback.setStatus(FeedbackStatus.CLOSED);
            feedback = feedbackRepository.save(feedback);
        }
        return toDto(feedback);
    }
}
