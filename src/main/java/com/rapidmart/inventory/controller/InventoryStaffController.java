package com.rapidmart.inventory.controller;

import com.rapidmart.common.enums.RestockRequestStatus;
import com.rapidmart.inventory.dto.*;
import com.rapidmart.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Inventory APIs. Reads are open to Inventory Officers (role INVENTORY_STAFF) and Managers; the restock
 * workflow enforces separation of duties per method: officers create and receive, managers approve/reject.
 */
@RestController
@RequestMapping("/api/v1/inventory")
@PreAuthorize("hasAnyRole('INVENTORY_STAFF', 'MANAGER')")
public class InventoryStaffController {

    private final InventoryService inventoryService;

    public InventoryStaffController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<InventoryDto>> getLowStock() {
        return ResponseEntity.ok(inventoryService.getLowStockInventory());
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<InventoryDto> getInventoryByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(inventoryService.getInventoryByProductId(productId));
    }

    @PutMapping("/products/{productId}/limits")
    public ResponseEntity<InventoryDto> updateLimits(@PathVariable Long productId,
                                                     @Valid @RequestBody UpdateInventoryLimitsRequest request) {
        return ResponseEntity.ok(inventoryService.updateInventoryLimits(productId, request));
    }

    /** Request history for Inventory Officers and the approval queue for Managers (filter with ?status=PENDING). */
    @GetMapping("/restock-requests")
    public ResponseEntity<Page<RestockRequestDto>> getRestockRequests(@RequestParam(required = false) RestockRequestStatus status,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(inventoryService.getRestockRequests(status, pageable));
    }

    @PostMapping("/restock-requests")
    @PreAuthorize("hasRole('INVENTORY_STAFF')")
    public ResponseEntity<RestockRequestDto> createRestockRequest(@Valid @RequestBody CreateRestockRequest request) {
        return new ResponseEntity<>(inventoryService.createRestockRequest(request), HttpStatus.CREATED);
    }

    /** Body is optional: {"approvedQuantity": n} approves part of the request (1..requestedQuantity). */
    @PutMapping("/restock-requests/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<RestockRequestDto> approveRestockRequest(@PathVariable Long id,
                                                                   @Valid @RequestBody(required = false) ApproveRestockRequest request) {
        Integer approvedQuantity = request != null ? request.getApprovedQuantity() : null;
        return ResponseEntity.ok(inventoryService.approveRestockRequest(id, approvedQuantity));
    }

    @PutMapping("/restock-requests/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<RestockRequestDto> rejectRestockRequest(@PathVariable Long id,
                                                                  @Valid @RequestBody RejectRestockRequest request) {
        return ResponseEntity.ok(inventoryService.rejectRestockRequest(id, request.getReason()));
    }

    /** quantityReceived is optional and defaults to the approved quantity. */
    @PostMapping("/restock-requests/{id}/receive")
    @PreAuthorize("hasRole('INVENTORY_STAFF')")
    public ResponseEntity<RestockRequestDto> receiveStock(@PathVariable Long id,
                                                          @RequestParam(required = false) Integer quantityReceived) {
        return ResponseEntity.ok(inventoryService.receiveStock(id, quantityReceived));
    }

    @GetMapping("/movements")
    public ResponseEntity<Page<StockMovementDto>> getMovementHistory(@RequestParam(required = false) Long productId,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(inventoryService.getMovementHistory(productId, pageable));
    }
}
