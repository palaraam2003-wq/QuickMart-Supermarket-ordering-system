package com.rapidmart.feedback.repository;

import com.rapidmart.feedback.entity.FeedbackReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackReplyRepository extends JpaRepository<FeedbackReply, Long> {

    List<FeedbackReply> findByFeedbackIdOrderByCreatedAtAsc(Long feedbackId);
}
