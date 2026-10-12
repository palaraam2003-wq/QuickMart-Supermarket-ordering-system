package com.rapidmart.delivery.controller;

import com.rapidmart.delivery.dto.CodSummaryDto;
import com.rapidmart.delivery.dto.DeliveryDto;
import com.rapidmart.delivery.service.CodSettlementService;
import com.rapidmart.delivery.service.DeliveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/delivery-staff")
@PreAuthorize("hasRole('DELIVERY_STAFF')")
public class DeliveryStaffController {

    private final DeliveryService deliveryService;
    private final CodSettlementService codSettlementService;

    public DeliveryStaffController(DeliveryService deliveryService, CodSettlementService codSettlementService) {
        this.deliveryService = deliveryService;
        this.codSettlementService = codSettlementService;
    }

    @GetMapping("/my-deliveries")
    public ResponseEntity<List<DeliveryDto>> getMyDeliveries() {
        return ResponseEntity.ok(deliveryService.getMyDeliveries());
    }

    @GetMapping("/cod-summary")
    public ResponseEntity<CodSummaryDto> getCodSummary() {
        return ResponseEntity.ok(codSettlementService.getMySummary());
    }

    @PostMapping("/deliveries/{id}/out-for-delivery")
    public ResponseEntity<DeliveryDto> markOutForDelivery(@PathVariable Long id) {
        return ResponseEntity.ok(deliveryService.toDto(deliveryService.markOutForDelivery(id)));
    }

    @PostMapping("/deliveries/{id}/failed")
    public ResponseEntity<DeliveryDto> recordFailedDelivery(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(deliveryService.toDto(deliveryService.recordFailedDelivery(id, reason)));
    }

    @PostMapping("/deliveries/{id}/complete")
    public ResponseEntity<DeliveryDto> completeDelivery(@PathVariable Long id,
                                                        @RequestParam(required = false) BigDecimal collectedAmount) {
        return ResponseEntity.ok(deliveryService.toDto(deliveryService.completeDelivery(id, collectedAmount)));
    }
}
