package com.rapidmart.delivery.controller;

import com.rapidmart.delivery.dto.DeliveryTrackingDto;
import com.rapidmart.delivery.dto.UpdateDeliveryStatusRequest;
import com.rapidmart.delivery.service.DeliveryTrackingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/deliveries", "/api/v1/deliveries"})
@PreAuthorize("hasAnyRole('DELIVERY_STAFF', 'DELIVERY_COORDINATOR', 'MANAGER')")
public class DeliveryController {

    private final DeliveryTrackingService deliveryTrackingService;

    public DeliveryController(DeliveryTrackingService deliveryTrackingService) {
        this.deliveryTrackingService = deliveryTrackingService;
    }

    @PatchMapping("/{deliveryId}/status")
    public ResponseEntity<DeliveryTrackingDto> updateDeliveryStatus(
            @PathVariable Long deliveryId,
            @Valid @RequestBody UpdateDeliveryStatusRequest request) {
        DeliveryTrackingDto tracking = deliveryTrackingService.updateDeliveryStatusByStaff(
                deliveryId, request.getStatus(), request.getMessage());
        return ResponseEntity.ok(tracking);
    }
}
