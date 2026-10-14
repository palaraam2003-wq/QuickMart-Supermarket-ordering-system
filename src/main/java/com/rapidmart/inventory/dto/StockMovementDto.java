package com.rapidmart.inventory.dto;

import com.rapidmart.common.enums.StockMovementType;

import java.time.OffsetDateTime;

public class StockMovementDto {
    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private Integer quantityDelta;
    private StockMovementType movementType;
    private String referenceId;
    private String actorEmail;
    private OffsetDateTime createdAt;

    public StockMovementDto() {
    }

    public StockMovementDto(Long id, Long productId, String productName, String productSku, Integer quantityDelta, StockMovementType movementType, String referenceId, String actorEmail, OffsetDateTime createdAt) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.productSku = productSku;
        this.quantityDelta = quantityDelta;
        this.movementType = movementType;
        this.referenceId = referenceId;
        this.actorEmail = actorEmail;
        this.createdAt = createdAt;
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

    public Integer getQuantityDelta() {
        return quantityDelta;
    }

    public void setQuantityDelta(Integer quantityDelta) {
        this.quantityDelta = quantityDelta;
    }

    public StockMovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(StockMovementType movementType) {
        this.movementType = movementType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public void setActorEmail(String actorEmail) {
        this.actorEmail = actorEmail;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
