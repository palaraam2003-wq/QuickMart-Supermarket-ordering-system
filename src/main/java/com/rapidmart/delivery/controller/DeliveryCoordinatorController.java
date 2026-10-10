package com.rapidmart.delivery.controller;

import com.rapidmart.common.enums.DeliveryStatus;
import com.rapidmart.auth.AuthService;
import com.rapidmart.delivery.dto.CreateDeliveryStaffRequest;
import com.rapidmart.delivery.dto.DelayedDeliveryDto;
import com.rapidmart.delivery.dto.DeliveryDto;
import com.rapidmart.delivery.dto.DeliveryStaffSummaryDto;
import com.rapidmart.delivery.dto.DriverSlotLimitDto;
import com.rapidmart.delivery.dto.SetDriverSlotLimitRequest;
import com.rapidmart.delivery.dto.UpdateDeliveryStaffRequest;
import com.rapidmart.delivery.service.DeliveryService;
import com.rapidmart.employee.service.EmployeeService;
import com.rapidmart.user.dto.UserResponseDto;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/delivery-coordinator")
@PreAuthorize("hasAnyRole('DELIVERY_COORDINATOR', 'MANAGER')")
public class DeliveryCoordinatorController {

    private final DeliveryService deliveryService;
    private final EmployeeService employeeService;
    private final AuthService authService;

    public DeliveryCoordinatorController(DeliveryService deliveryService,
                                         EmployeeService employeeService,
                                         AuthService authService) {
        this.deliveryService = deliveryService;
        this.employeeService = employeeService;
        this.authService = authService;
    }

    @GetMapping("/deliveries")
    public ResponseEntity<List<DeliveryDto>> getAllDeliveries(@RequestParam(required = false) DeliveryStatus status) {
        return ResponseEntity.ok(deliveryService.getAllDeliveries(status));
    }

    @GetMapping("/staff")
    public ResponseEntity<List<DeliveryStaffSummaryDto>> getDeliveryStaff(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate availabilityDate,
            @RequestParam(required = false) String availabilitySlot,
            @RequestParam(required = false) Boolean available) {
        return ResponseEntity.ok(deliveryService.getDeliveryStaff(
                search, active, availabilityDate, availabilitySlot, available));
    }

    @PostMapping("/staff")
    public ResponseEntity<UserResponseDto> createDeliveryStaff(@Valid @RequestBody CreateDeliveryStaffRequest request) {
        return new ResponseEntity<>(authService.mapToDto(employeeService.createDeliveryStaff(
                request.email(), request.password(), request.fullName(), request.phone())), HttpStatus.CREATED);
    }

    @PutMapping("/staff/{id}")
    public ResponseEntity<UserResponseDto> updateDeliveryStaff(@PathVariable Long id,
                                                               @Valid @RequestBody UpdateDeliveryStaffRequest request) {
        employeeService.requireCoordinatorManagedDeliveryStaff(id);
        if (!request.active()) {
            deliveryService.assertNoActiveAssignments(id);
        }
        return ResponseEntity.ok(authService.mapToDto(employeeService.updateDeliveryStaff(
                id, request.fullName(), request.phone(), request.active())));
    }

    @GetMapping("/staff/{id}/assignments")
    public ResponseEntity<List<DeliveryDto>> getDeliveryStaffAssignments(@PathVariable Long id) {
        return ResponseEntity.ok(deliveryService.getDeliveryStaffAssignments(id));
    }

    @PostMapping("/deliveries/{id}/assign")
    public ResponseEntity<DeliveryDto> assignStaff(@PathVariable Long id,
                                                   @RequestParam Long staffId,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduledDate,
                                                   @RequestParam String scheduledSlot) {
        return ResponseEntity.ok(deliveryService.toDto(deliveryService.assignDeliveryStaff(id, staffId, scheduledDate, scheduledSlot)));
    }

    @PostMapping("/deliveries/{id}/returned-items-checked")
    public ResponseEntity<DeliveryDto> markReturnedItemsChecked(@PathVariable Long id) {
        return ResponseEntity.ok(deliveryService.toDto(deliveryService.markReturnedItemsChecked(id)));
    }

    @PostMapping("/deliveries/{id}/cancel")
    public ResponseEntity<DeliveryDto> cancelFailedDelivery(@PathVariable Long id,
                                                            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(deliveryService.toDto(deliveryService.cancelFailedDelivery(id, reason)));
    }

    // --- Driver Slot Limit Endpoints ---

    @PostMapping("/slot-limits")
    public ResponseEntity<DriverSlotLimitDto> setDriverSlotLimit(@Valid @RequestBody SetDriverSlotLimitRequest request) {
        DriverSlotLimitDto dto = deliveryService.setDriverSlotLimit(
                request.getDriverId(),
                request.getScheduledDate(),
                request.getScheduledSlot(),
                request.getMaxOrders()
        );
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/slot-limits")
    public ResponseEntity<List<DriverSlotLimitDto>> getDriverSlotLimits(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduledDate,
            @RequestParam(required = false) String scheduledSlot,
            @RequestParam(required = false) Long driverId) {
        return ResponseEntity.ok(deliveryService.getDriverSlotLimits(scheduledDate, scheduledSlot, driverId));
    }

    @DeleteMapping("/slot-limits/{id}")
    public ResponseEntity<Void> deleteDriverSlotLimit(@PathVariable Long id) {
        deliveryService.deleteDriverSlotLimit(id);
        return ResponseEntity.noContent().build();
    }

    // --- Delayed Deliveries Audit Endpoint ---

    @GetMapping("/delayed-deliveries")
    public ResponseEntity<List<DelayedDeliveryDto>> getDelayedDeliveries() {
        return ResponseEntity.ok(deliveryService.getDelayedDeliveries());
    }

    @PostMapping("/check-overdue")
    public ResponseEntity<Void> checkOverdueDeliveries() {
        deliveryService.checkAndUpdateOverdueDeliveries();
        return ResponseEntity.ok().build();
    }
}
