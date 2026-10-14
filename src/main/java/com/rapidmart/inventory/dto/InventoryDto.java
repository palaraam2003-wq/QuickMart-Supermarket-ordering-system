package com.rapidmart.inventory.dto;

public class InventoryDto {
    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private Integer quantity;
    private Integer minimumLevel;
    private Integer maximumLevel;

    public InventoryDto() {
    }

    public InventoryDto(Long id, Long productId, String productName, String productSku, Integer quantity, Integer minimumLevel, Integer maximumLevel) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.productSku = productSku;
        this.quantity = quantity;
        this.minimumLevel = minimumLevel;
        this.maximumLevel = maximumLevel;
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

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
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
}
