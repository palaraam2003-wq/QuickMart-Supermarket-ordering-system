package com.rapidmart.employee.controller;

import com.rapidmart.audit.entity.AuditLog;
import com.rapidmart.audit.service.AuditService;
import com.rapidmart.auth.AuthService;
import com.rapidmart.employee.dto.CreateEmployeeRequest;
import com.rapidmart.employee.dto.UpdateEmployeeRequest;
import com.rapidmart.employee.service.EmployeeService;
import com.rapidmart.feedback.dto.FeedbackDto;
import com.rapidmart.feedback.dto.FeedbackReplyRequest;
import com.rapidmart.feedback.service.FeedbackService;
import com.rapidmart.report.service.ReportService;
import com.rapidmart.user.dto.UserResponseDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/manager")
@PreAuthorize("hasRole('MANAGER')")
public class ManagerController {

    private final EmployeeService employeeService;
    private final ReportService reportService;
    private final AuditService auditService;
    private final FeedbackService feedbackService;
    private final AuthService authService;

    public ManagerController(EmployeeService employeeService,
                             ReportService reportService,
                             AuditService auditService,
                             FeedbackService feedbackService,
                             AuthService authService) {
        this.employeeService = employeeService;
        this.reportService = reportService;
        this.auditService = auditService;
        this.feedbackService = feedbackService;
        this.authService = authService;
    }

    // Employees
    @GetMapping("/employees")
    public ResponseEntity<List<UserResponseDto>> getAllEmployees() {
        return ResponseEntity.ok(employeeService.getAllEmployees().stream().map(authService::mapToDto).toList());
    }

    @PostMapping("/employees")
    public ResponseEntity<UserResponseDto> createEmployee(@Valid @RequestBody CreateEmployeeRequest request) {
        return new ResponseEntity<>(authService.mapToDto(employeeService.createEmployee(
                request.email(), request.password(), request.fullName(), request.phone(), request.roles())), HttpStatus.CREATED);
    }

    @PutMapping("/employees/{id}")
    public ResponseEntity<UserResponseDto> updateEmployee(@PathVariable Long id,
                                                          @Valid @RequestBody UpdateEmployeeRequest request) {
        return ResponseEntity.ok(authService.mapToDto(employeeService.updateEmployee(
                id, request.fullName(), request.phone(), request.roles(), request.active())));
    }

    // Reports
    @GetMapping("/reports/sales")
    public ResponseEntity<Map<String, Object>> getSalesSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(reportService.getSalesSummary(startDate, endDate));
    }

    @GetMapping("/reports/sales/csv")
    public ResponseEntity<String> exportSalesCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        String csvData = reportService.generateSalesCsv(startDate, endDate);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sales-report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }

    @GetMapping("/reports/inventory")
    public ResponseEntity<Map<String, Object>> getInventorySummary() {
        return ResponseEntity.ok(reportService.getInventorySummary());
    }

    @GetMapping("/reports/delivery")
    public ResponseEntity<Map<String, Object>> getDeliverySummary() {
        return ResponseEntity.ok(reportService.getDeliverySummary());
    }

    // Audit Logs
    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(@RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return ResponseEntity.ok(auditService.getAuditLogs(pageable));
    }

    // Feedback Management
    @GetMapping("/feedback")
    public ResponseEntity<Page<FeedbackDto>> getAllFeedback(@RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return ResponseEntity.ok(feedbackService.getAllFeedback(pageable));
    }

    @PostMapping("/feedback/{id}/reply")
    public ResponseEntity<FeedbackDto> replyToFeedback(@PathVariable Long id, @Valid @RequestBody FeedbackReplyRequest request) {
        return ResponseEntity.ok(feedbackService.replyToFeedback(id, request.replyMessage(), request.category()));
    }

    @PostMapping("/feedback/{id}/close")
    public ResponseEntity<FeedbackDto> closeFeedback(@PathVariable Long id) {
        return ResponseEntity.ok(feedbackService.closeFeedback(id));
    }
}
