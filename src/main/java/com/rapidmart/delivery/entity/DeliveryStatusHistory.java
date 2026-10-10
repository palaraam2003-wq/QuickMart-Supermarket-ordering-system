package com.rapidmart.delivery.entity;

import com.rapidmart.common.entity.BaseEntity;
import com.rapidmart.common.enums.DeliveryStatus;
import com.rapidmart.order.entity.Order;
import jakarta.persistence.*;

@Entity
@Table(name = "delivery_status_history", indexes = {
    @Index(name = "idx_dsh_delivery_id", columnList = "delivery_id"),
    @Index(name = "idx_dsh_order_id", columnList = "order_id")
})
public class DeliveryStatusHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private Delivery delivery;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private DeliveryStatus status;

    @Column(name = "status_message", length = 500)
    private String statusMessage;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    public DeliveryStatusHistory() {
    }

    public DeliveryStatusHistory(Delivery delivery, Order order, DeliveryStatus status, String statusMessage, String updatedBy) {
        this.delivery = delivery;
        this.order = order;
        this.status = status;
        this.statusMessage = statusMessage;
        this.updatedBy = updatedBy;
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

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(DeliveryStatus status) {
        this.status = status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
