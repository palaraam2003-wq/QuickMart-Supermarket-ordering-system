package com.rapidmart.inventory.entity;

import com.rapidmart.common.entity.BaseEntity;
import com.rapidmart.product.entity.Product;
import jakarta.persistence.*;

@Entity
@Table(name = "inventory")
public class Inventory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(nullable = false)
    private Integer quantity = 0;

    @Column(name = "minimum_level", nullable = false)
    private Integer minimumLevel = 10;

    @Column(name = "maximum_level", nullable = false)
    private Integer maximumLevel = 1000;

    @Version
    private Long version;

    public Inventory() {
    }

    public Inventory(Product product, Integer quantity, Integer minimumLevel, Integer maximumLevel) {
        this.product = product;
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

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
