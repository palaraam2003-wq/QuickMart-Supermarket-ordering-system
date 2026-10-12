package com.rapidmart.delivery.entity;

import com.rapidmart.common.entity.BaseEntity;
import com.rapidmart.user.entity.User;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "driver_slot_limits", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"driver_id", "scheduled_date", "scheduled_slot"})
})
public class DriverSlotLimit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "scheduled_slot", nullable = false)
    private String scheduledSlot;

    @Column(name = "max_orders", nullable = false)
    private Integer maxOrders;

    public DriverSlotLimit() {
    }

    public DriverSlotLimit(User driver, LocalDate scheduledDate, String scheduledSlot, Integer maxOrders) {
        this.driver = driver;
        this.scheduledDate = scheduledDate;
        this.scheduledSlot = scheduledSlot;
        this.maxOrders = maxOrders;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getDriver() {
        return driver;
    }

    public void setDriver(User driver) {
        this.driver = driver;
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

    public Integer getMaxOrders() {
        return maxOrders;
    }

    public void setMaxOrders(Integer maxOrders) {
        this.maxOrders = maxOrders;
    }
}
