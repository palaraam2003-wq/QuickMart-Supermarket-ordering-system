package com.rapidmart.feedback.controller;

import com.rapidmart.feedback.dto.CreateFeedbackRequest;
import com.rapidmart.feedback.dto.FeedbackDto;
import com.rapidmart.feedback.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer/feedback")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerFeedbackController {

    private final FeedbackService feedbackService;

    public CustomerFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ResponseEntity<FeedbackDto> submitFeedback(@Valid @RequestBody CreateFeedbackRequest request) {
        return new ResponseEntity<>(feedbackService.submitFeedback(request.category(), request.subject(), request.message()), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<FeedbackDto>> getMyFeedback() {
        return ResponseEntity.ok(feedbackService.getMyFeedback());
    }
}
