package com.rapidmart.delivery.entity;

import com.rapidmart.common.entity.BaseEntity;
import com.rapidmart.common.enums.DeliveryStatus;
import com.rapidmart.order.entity.Order;
import com.rapidmart.user.entity.User;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "deliveries")
public class Delivery extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_staff_id")
    private User assignedStaff;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "scheduled_slot", nullable = false)
    private String scheduledSlot;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "returned_items_checked", nullable = false)
    private boolean returnedItemsChecked = false;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DeliveryAttempt> attempts = new ArrayList<>();

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC, id ASC")
    private List<DeliveryStatusHistory> statusHistory = new ArrayList<>();

    public Delivery() {
    }

    public Delivery(Order order, LocalDate scheduledDate, String scheduledSlot) {
        this.order = order;
        this.scheduledDate = scheduledDate;
        this.scheduledSlot = scheduledSlot;
        this.deliveryStatus = DeliveryStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public User getAssignedStaff() {
        return assignedStaff;
    }

    public void setAssignedStaff(User assignedStaff) {
        this.assignedStaff = assignedStaff;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public String getScheduledSlot() {
        return scheduledSlot;
    }

    public void setScheduledSlot(String scheduledSlot) {
        this.scheduledSlot = scheduledSlot;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public boolean isReturnedItemsChecked() {
        return returnedItemsChecked;
    }

    public void setReturnedItemsChecked(boolean returnedItemsChecked) {
        this.returnedItemsChecked = returnedItemsChecked;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public List<DeliveryAttempt> getAttempts() {
        return attempts;
    }

    public void setAttempts(List<DeliveryAttempt> attempts) {
        this.attempts = attempts;
    }

    public List<DeliveryStatusHistory> getStatusHistory() {
        return statusHistory;
    }

    public void setStatusHistory(List<DeliveryStatusHistory> statusHistory) {
        this.statusHistory = statusHistory;
    }
}
