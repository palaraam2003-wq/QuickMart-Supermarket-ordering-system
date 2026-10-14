package com.rapidmart.inventory.dto;

import com.rapidmart.common.enums.RestockRequestStatus;

import java.time.OffsetDateTime;

/** A restock request with its full audit trail and the product's current stock position. */
public class RestockRequestDto {
    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private Integer currentStock;
    private Integer minimumLevel;
    private Integer maximumLevel;
    private RestockRequestStatus status;
    private Integer requestedQuantity;
    private Integer approvedQuantity;
    private Integer receivedQuantity;
    private Long requestedById;
    private String requestedByEmail;
    private String requestedByName;
    private OffsetDateTime requestedAt;
    private String approvedByEmail;
    private OffsetDateTime approvedAt;
    private String rejectedByEmail;
    private OffsetDateTime rejectedAt;
    private String rejectionReason;
    private String receivedByEmail;
    private OffsetDateTime receivedAt;
    private OffsetDateTime createdAt;

    public RestockRequestDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductSku() {
        return productSku;
    }

    public void setProductSku(String productSku) {
        this.productSku = productSku;
    }

    public Integer getCurrentStock() {
        return currentStock;
    }

    public void setCurrentStock(Integer currentStock) {
        this.currentStock = currentStock;
    }

    public Integer getMinimumLevel() {
        return minimumLevel;
    }

    public void setMinimumLevel(Integer minimumLevel) {
        this.minimumLevel = minimumLevel;
    }

    public Integer getMaximumLevel() {
        return maximumLevel;
    }

    public void setMaximumLevel(Integer maximumLevel) {
        this.maximumLevel = maximumLevel;
    }

    public RestockRequestStatus getStatus() {
        return status;
    }

    public void setStatus(RestockRequestStatus status) {
        this.status = status;
    }

    public Integer getRequestedQuantity() {
        return requestedQuantity;
    }

    public void setRequestedQuantity(Integer requestedQuantity) {
        this.requestedQuantity = requestedQuantity;
    }

    public Integer getApprovedQuantity() {
        return approvedQuantity;
    }

    public void setApprovedQuantity(Integer approvedQuantity) {
        this.approvedQuantity = approvedQuantity;
    }

    public Integer getReceivedQuantity() {
        return receivedQuantity;
    }

    public void setReceivedQuantity(Integer receivedQuantity) {
        this.receivedQuantity = receivedQuantity;
    }

    public Long getRequestedById() {
        return requestedById;
    }

    public void setRequestedById(Long requestedById) {
        this.requestedById = requestedById;
    }

    public String getRequestedByEmail() {
        return requestedByEmail;
    }

    public void setRequestedByEmail(String requestedByEmail) {
        this.requestedByEmail = requestedByEmail;
    }

    public String getRequestedByName() {
        return requestedByName;
    }

    public void setRequestedByName(String requestedByName) {
        this.requestedByName = requestedByName;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(OffsetDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public String getApprovedByEmail() {
        return approvedByEmail;
    }

    public void setApprovedByEmail(String approvedByEmail) {
        this.approvedByEmail = approvedByEmail;
    }

    public OffsetDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(OffsetDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getRejectedByEmail() {
        return rejectedByEmail;
    }

    public void setRejectedByEmail(String rejectedByEmail) {
        this.rejectedByEmail = rejectedByEmail;
    }

    public OffsetDateTime getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(OffsetDateTime rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getReceivedByEmail() {
        return receivedByEmail;
    }

    public void setReceivedByEmail(String receivedByEmail) {
        this.receivedByEmail = receivedByEmail;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
