package com.rapidmart.inventory.service;

import com.rapidmart.audit.service.AuditService;
import com.rapidmart.common.enums.RestockRequestStatus;
import com.rapidmart.common.enums.StockMovementType;
import com.rapidmart.common.exception.BusinessException;
import com.rapidmart.common.exception.ResourceNotFoundException;
import com.rapidmart.inventory.dto.*;
import com.rapidmart.inventory.entity.Inventory;
import com.rapidmart.inventory.entity.RestockRequest;
import com.rapidmart.inventory.entity.StockMovementHistory;
import com.rapidmart.inventory.repository.InventoryRepository;
import com.rapidmart.inventory.repository.RestockRequestRepository;
import com.rapidmart.inventory.repository.StockMovementHistoryRepository;
import com.rapidmart.product.entity.Product;
import com.rapidmart.product.repository.ProductRepository;
import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final StockMovementHistoryRepository stockMovementHistoryRepository;
    private final RestockRequestRepository restockRequestRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public InventoryService(InventoryRepository inventoryRepository,
                            ProductRepository productRepository,
                            StockMovementHistoryRepository stockMovementHistoryRepository,
                            RestockRequestRepository restockRequestRepository,
                            UserRepository userRepository,
                            AuditService auditService) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.stockMovementHistoryRepository = stockMovementHistoryRepository;
        this.restockRequestRepository = restockRequestRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<InventoryDto> getLowStockInventory() {
        return inventoryRepository.findLowStockInventory()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InventoryDto getInventoryByProductId(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", productId));
        return mapToDto(inventory);
    }

    @Transactional
    public InventoryDto updateInventoryLimits(Long productId, UpdateInventoryLimitsRequest request) {
        if (request.getMinimumLevel() >= request.getMaximumLevel()) {
            throw new BusinessException("INVALID_LIMITS", "Minimum level must be strictly less than maximum level.");
        }

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", productId));

        inventory.setMinimumLevel(request.getMinimumLevel());
        inventory.setMaximumLevel(request.getMaximumLevel());

        Inventory saved = inventoryRepository.save(inventory);
        return mapToDto(saved);
    }

    // ---- Restock workflow: Inventory Officer requests -> Manager approves/rejects -> Inventory Officer receives ----
    // Allowed transitions: PENDING -> APPROVED -> RECEIVED and PENDING -> REJECTED (see RestockRequestStatus).

    @PreAuthorize("hasRole('INVENTORY_STAFF')")
    @Transactional
    public RestockRequestDto createRestockRequest(CreateRestockRequest request) {
        if (request.getRequestedQuantity() == null || request.getRequestedQuantity() <= 0) {
            throw new BusinessException("INVALID_QUANTITY", "Requested quantity must be greater than zero.");
        }
        User user = currentUser();

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId()));
        inventoryRepository.findByProductId(product.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", product.getId()));

        if (restockRequestRepository.existsByProductIdAndStatusIn(product.getId(), RestockRequestStatus.OPEN)) {
            throw duplicateOpenRequest(product);
        }

        RestockRequest saved;
        try {
            // The filtered unique index uk_restock_open_product closes the race between two concurrent creates.
            saved = restockRequestRepository.saveAndFlush(new RestockRequest(product, request.getRequestedQuantity(), user));
        } catch (DataIntegrityViolationException ex) {
            throw duplicateOpenRequest(product);
        }
        auditService.logAction("RESTOCK_REQUESTED", user.getEmail(), restockReference(saved.getId()),
                String.format("%s (%s): %d units requested", product.getName(), product.getSku(), saved.getRequestedQuantity()));
        return mapToRestockDto(saved);
    }

    /**
     * Manager approval. Does not touch inventory: stock only changes when the approved goods are received.
     * approvedQuantity is optional (defaults to the requested quantity) and must be 1..requestedQuantity.
     */
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public RestockRequestDto approveRestockRequest(Long requestId, Integer approvedQuantity) {
        User manager = currentUser();
        RestockRequest request = lockRestockRequest(requestId);
        requireTransition(request, RestockRequestStatus.APPROVED);
        requireDifferentReviewer(request, manager);

        int quantity = approvedQuantity != null ? approvedQuantity : request.getRequestedQuantity();
        if (quantity < 1 || quantity > request.getRequestedQuantity()) {
            throw new BusinessException("INVALID_QUANTITY",
                    String.format("Approved quantity must be between 1 and the requested quantity (%d).", request.getRequestedQuantity()));
        }

        request.setStatus(RestockRequestStatus.APPROVED);
        request.setApprovedBy(manager);
        request.setApprovedAt(OffsetDateTime.now());
        request.setApprovedQuantity(quantity);
        RestockRequest saved = restockRequestRepository.save(request);
        auditService.logAction("RESTOCK_APPROVED", manager.getEmail(), restockReference(requestId),
                String.format("%d of %d requested units approved", quantity, request.getRequestedQuantity()));
        return mapToRestockDto(saved);
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public RestockRequestDto rejectRestockRequest(Long requestId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("REASON_REQUIRED", "A rejection reason is required.");
        }
        User manager = currentUser();
        RestockRequest request = lockRestockRequest(requestId);
        requireTransition(request, RestockRequestStatus.REJECTED);
        requireDifferentReviewer(request, manager);

        request.setStatus(RestockRequestStatus.REJECTED);
        request.setRejectedBy(manager);
        request.setRejectedAt(OffsetDateTime.now());
        request.setRejectionReason(reason.trim());
        RestockRequest saved = restockRequestRepository.save(request);
        auditService.logAction("RESTOCK_REJECTED", manager.getEmail(), restockReference(requestId), reason.trim());
        return mapToRestockDto(saved);
    }

    /**
     * Records the physical receipt of approved stock. The request row and then the inventory row are locked,
     * so a concurrent or repeated receipt waits, then sees RECEIVED and fails with 409. The inventory increase,
     * the status change and the single RECEIVED_STOCK movement commit (or roll back) together.
     *
     * @param quantityReceived optional; defaults to the approved quantity
     */
    @PreAuthorize("hasRole('INVENTORY_STAFF')")
    @Transactional
    public RestockRequestDto receiveStock(Long requestId, Integer quantityReceived) {
        if (quantityReceived != null && quantityReceived <= 0) {
            throw new BusinessException("INVALID_QUANTITY", "Received quantity must be greater than zero.");
        }
        User actor = currentUser();

        // Must be the first load of this request in the persistence context, so the locked read is fresh.
        RestockRequest request = lockRestockRequest(requestId);
        if (request.getStatus() == RestockRequestStatus.RECEIVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "ALREADY_RECEIVED",
                    "Stock for restock request #" + requestId + " has already been received.");
        }
        requireTransition(request, RestockRequestStatus.RECEIVED);

        int receivable = request.getReceivableQuantity();
        int quantity = quantityReceived != null ? quantityReceived : receivable;
        if (quantity > receivable) {
            throw new BusinessException("INVALID_QUANTITY",
                    String.format("Received quantity (%d) cannot exceed the approved quantity (%d).", quantity, receivable));
        }

        Long productId = request.getProduct().getId();
        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", productId));
        if (inventory.getQuantity() + quantity > inventory.getMaximumLevel()) {
            throw new BusinessException("EXCEEDS_MAX_LEVEL",
                    String.format("Receiving %d units would take stock to %d, above the maximum level of %d.",
                            quantity, inventory.getQuantity() + quantity, inventory.getMaximumLevel()));
        }

        inventory.setQuantity(inventory.getQuantity() + quantity);
        inventoryRepository.save(inventory);

        request.setStatus(RestockRequestStatus.RECEIVED);
        request.setReceivedQuantity(quantity);
        request.setReceivedBy(actor);
        request.setReceivedAt(OffsetDateTime.now());
        RestockRequest savedRequest = restockRequestRepository.save(request);

        recordMovement(request.getProduct(), quantity, StockMovementType.RECEIVED_STOCK,
                restockReference(requestId), actor.getId(), actor.getEmail());
        auditService.logAction("RESTOCK_RECEIVED", actor.getEmail(), restockReference(requestId),
                String.format("%d units received; stock now %d", quantity, inventory.getQuantity()));

        return mapToRestockDto(savedRequest, inventory);
    }

    /** Movement/audit reference for a restock request, e.g. RESTOCK_REQ_42. */
    public static String restockReference(Long requestId) {
        return "RESTOCK_REQ_" + requestId;
    }

    private User currentUser() {
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));
    }

    private RestockRequest lockRestockRequest(Long requestId) {
        return restockRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("RestockRequest", "id", requestId));
    }

    private static void requireTransition(RestockRequest request, RestockRequestStatus next) {
        if (!request.getStatus().canTransitionTo(next)) {
            throw new BusinessException(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION",
                    String.format("Restock request #%d is %s and cannot be moved to %s.", request.getId(), request.getStatus(), next));
        }
    }

    /** Separation of duties: nobody reviews their own request, whatever roles they hold. */
    private static void requireDifferentReviewer(RestockRequest request, User reviewer) {
        if (request.getRequestedBy().getId().equals(reviewer.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "SELF_APPROVAL_FORBIDDEN",
                    "You cannot approve or reject a restock request you created. Another manager must review it.");
        }
    }

    private static BusinessException duplicateOpenRequest(Product product) {
        return new BusinessException(HttpStatus.CONFLICT, "DUPLICATE_OPEN_REQUEST",
                String.format("%s (%s) already has a pending or approved restock request. Complete or resolve it first.",
                        product.getName(), product.getSku()));
    }

    @Transactional
    public void recordMovement(Product product, Integer quantityDelta, StockMovementType movementType, String referenceId, Long actorId, String actorEmail) {
        StockMovementHistory movement = new StockMovementHistory(
                product, quantityDelta, movementType, referenceId, actorId, actorEmail
        );
        stockMovementHistoryRepository.save(movement);
    }

    @Transactional(readOnly = true)
    public Page<StockMovementDto> getMovementHistory(Long productId, Pageable pageable) {
        Page<StockMovementHistory> history;
        if (productId != null) {
            history = stockMovementHistoryRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable);
        } else {
            history = stockMovementHistoryRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return history.map(this::mapToMovementDto);
    }

    /** All restock requests (optionally one status), newest first, with each product's current stock position. */
    @Transactional(readOnly = true)
    public Page<RestockRequestDto> getRestockRequests(RestockRequestStatus status, Pageable pageable) {
        Page<RestockRequest> page = status != null
                ? restockRequestRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : restockRequestRepository.findAllByOrderByCreatedAtDesc(pageable);
        Set<Long> productIds = page.getContent().stream().map(r -> r.getProduct().getId()).collect(Collectors.toSet());
        Map<Long, Inventory> stock = productIds.isEmpty() ? Map.of()
                : inventoryRepository.findByProductIdIn(productIds).stream()
                        .collect(Collectors.toMap(i -> i.getProduct().getId(), Function.identity()));
        return page.map(r -> mapToRestockDto(r, stock.get(r.getProduct().getId())));
    }

    public InventoryDto mapToDto(Inventory inventory) {
        return new InventoryDto(
                inventory.getId(),
                inventory.getProduct().getId(),
                inventory.getProduct().getName(),
                inventory.getProduct().getSku(),
                inventory.getQuantity(),
                inventory.getMinimumLevel(),
                inventory.getMaximumLevel()
        );
    }

    public StockMovementDto mapToMovementDto(StockMovementHistory movement) {
        return new StockMovementDto(
                movement.getId(),
                movement.getProduct().getId(),
                movement.getProduct().getName(),
                movement.getProduct().getSku(),
                movement.getQuantityDelta(),
                movement.getMovementType(),
                movement.getReferenceId(),
                movement.getActorEmail(),
                movement.getCreatedAt()
        );
    }

    public RestockRequestDto mapToRestockDto(RestockRequest request) {
        return mapToRestockDto(request, inventoryRepository.findByProductId(request.getProduct().getId()).orElse(null));
    }

    private RestockRequestDto mapToRestockDto(RestockRequest request, Inventory inventory) {
        RestockRequestDto dto = new RestockRequestDto();
        dto.setId(request.getId());
        dto.setProductId(request.getProduct().getId());
        dto.setProductName(request.getProduct().getName());
        dto.setProductSku(request.getProduct().getSku());
        if (inventory != null) {
            dto.setCurrentStock(inventory.getQuantity());
            dto.setMinimumLevel(inventory.getMinimumLevel());
            dto.setMaximumLevel(inventory.getMaximumLevel());
        }
        dto.setStatus(request.getStatus());
        dto.setRequestedQuantity(request.getRequestedQuantity());
        dto.setApprovedQuantity(request.getApprovedQuantity());
        dto.setReceivedQuantity(request.getReceivedQuantity());
        dto.setRequestedById(request.getRequestedBy().getId());
        dto.setRequestedByEmail(request.getRequestedBy().getEmail());
        dto.setRequestedByName(request.getRequestedBy().getFullName());
        dto.setRequestedAt(request.getRequestedAt() != null ? request.getRequestedAt() : request.getCreatedAt());
        dto.setApprovedByEmail(emailOf(request.getApprovedBy()));
        dto.setApprovedAt(request.getApprovedAt());
        dto.setRejectedByEmail(emailOf(request.getRejectedBy()));
        dto.setRejectedAt(request.getRejectedAt());
        dto.setRejectionReason(request.getRejectionReason());
        dto.setReceivedByEmail(emailOf(request.getReceivedBy()));
        dto.setReceivedAt(request.getReceivedAt());
        dto.setCreatedAt(request.getCreatedAt());
        return dto;
    }

    private static String emailOf(User user) {
        return user != null ? user.getEmail() : null;
    }
}
