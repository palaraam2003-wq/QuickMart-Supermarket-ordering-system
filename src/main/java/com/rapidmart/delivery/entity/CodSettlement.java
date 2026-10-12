package com.rapidmart.delivery.entity;

import com.rapidmart.user.entity.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "cod_settlements")
public class CodSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false, unique = true)
    private Delivery delivery;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_staff_id", nullable = false)
    private User deliveryStaff;

    @Column(name = "collected_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal collectedAmount;

    @Column(name = "confirmed_by_cashier", nullable = false)
    private boolean confirmedByCashier = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_id")
    private User cashier;

    @Column(name = "settled_at")
    private OffsetDateTime settledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    public CodSettlement() {
    }

    public CodSettlement(Delivery delivery, User deliveryStaff, BigDecimal collectedAmount) {
        this.delivery = delivery;
        this.deliveryStaff = deliveryStaff;
        this.collectedAmount = collectedAmount;
        this.confirmedByCashier = false;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Delivery getDelivery() {
        return delivery;
    }

    public void setDelivery(Delivery delivery) {
        this.delivery = delivery;
    }

    public User getDeliveryStaff() {
        return deliveryStaff;
    }

    public void setDeliveryStaff(User deliveryStaff) {
        this.deliveryStaff = deliveryStaff;
    }

    public BigDecimal getCollectedAmount() {
        return collectedAmount;
    }

    public void setCollectedAmount(BigDecimal collectedAmount) {
        this.collectedAmount = collectedAmount;
    }

    public boolean isConfirmedByCashier() {
        return confirmedByCashier;
    }

    public void setConfirmedByCashier(boolean confirmedByCashier) {
        this.confirmedByCashier = confirmedByCashier;
    }

    public User getCashier() {
        return cashier;
    }

    public void setCashier(User cashier) {
        this.cashier = cashier;
    }

    public OffsetDateTime getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(OffsetDateTime settledAt) {
        this.settledAt = settledAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
