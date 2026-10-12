package com.rapidmart.delivery.service;

import com.rapidmart.common.enums.DeliveryStatus;
import com.rapidmart.common.enums.OrderStatus;
import com.rapidmart.common.enums.Role;
import com.rapidmart.common.exception.BusinessException;
import com.rapidmart.common.exception.ResourceNotFoundException;
import com.rapidmart.delivery.dto.DeliveryStatusHistoryDto;
import com.rapidmart.delivery.dto.DeliveryTrackingDto;
import com.rapidmart.delivery.entity.Delivery;
import com.rapidmart.delivery.entity.DeliveryStatusHistory;
import com.rapidmart.delivery.repository.DeliveryRepository;
import com.rapidmart.delivery.repository.DeliveryStatusHistoryRepository;
import com.rapidmart.order.entity.Order;
import com.rapidmart.order.repository.OrderRepository;
import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.UserRepository;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class DeliveryTrackingService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryStatusHistoryRepository historyRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    // Allowed status transition rules map
    private static final Map<DeliveryStatus, Set<DeliveryStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(DeliveryStatus.class);

    static {
        // From ORDER_CONFIRMED, PENDING, SCHEDULED
        Set<DeliveryStatus> fromConfirmed = EnumSet.of(
                DeliveryStatus.ORDER_CONFIRMED,
                DeliveryStatus.PENDING,
                DeliveryStatus.SCHEDULED,
                DeliveryStatus.PROCESSING,
                DeliveryStatus.READY_FOR_DELIVERY,
                DeliveryStatus.ASSIGNED,
                DeliveryStatus.RESCHEDULED,
                DeliveryStatus.OUT_FOR_DELIVERY,
                DeliveryStatus.OVERDUE,
                DeliveryStatus.CANCELLED
        );
        ALLOWED_TRANSITIONS.put(DeliveryStatus.ORDER_CONFIRMED, fromConfirmed);
        ALLOWED_TRANSITIONS.put(DeliveryStatus.PENDING, fromConfirmed);
        ALLOWED_TRANSITIONS.put(DeliveryStatus.SCHEDULED, fromConfirmed);

        // From PROCESSING
        ALLOWED_TRANSITIONS.put(DeliveryStatus.PROCESSING, EnumSet.of(
                DeliveryStatus.PROCESSING,
                DeliveryStatus.READY_FOR_DELIVERY,
                DeliveryStatus.OUT_FOR_DELIVERY,
                DeliveryStatus.OVERDUE,
                DeliveryStatus.CANCELLED
        ));

        // From READY_FOR_DELIVERY, ASSIGNED, SCHEDULED, RESCHEDULED
        Set<DeliveryStatus> fromReady = EnumSet.of(
                DeliveryStatus.READY_FOR_DELIVERY,
                DeliveryStatus.PROCESSING,
                DeliveryStatus.ASSIGNED,
                DeliveryStatus.SCHEDULED,
                DeliveryStatus.RESCHEDULED,
                DeliveryStatus.OUT_FOR_DELIVERY,
                DeliveryStatus.OVERDUE,
                DeliveryStatus.CANCELLED
        );
        ALLOWED_TRANSITIONS.put(DeliveryStatus.READY_FOR_DELIVERY, fromReady);
        ALLOWED_TRANSITIONS.put(DeliveryStatus.ASSIGNED, fromReady);
        ALLOWED_TRANSITIONS.put(DeliveryStatus.RESCHEDULED, fromReady);

        // From OUT_FOR_DELIVERY
        ALLOWED_TRANSITIONS.put(DeliveryStatus.OUT_FOR_DELIVERY, EnumSet.of(
                DeliveryStatus.OUT_FOR_DELIVERY,
                DeliveryStatus.DELIVERED,
                DeliveryStatus.DELIVERED_ON_TIME,
                DeliveryStatus.DELIVERED_LATE,
                DeliveryStatus.OVERDUE,
                DeliveryStatus.DELIVERY_FAILED,
                DeliveryStatus.FAILED,
                DeliveryStatus.RETURNED
        ));

        // From OVERDUE
        ALLOWED_TRANSITIONS.put(DeliveryStatus.OVERDUE, EnumSet.of(
                DeliveryStatus.OVERDUE,
                DeliveryStatus.ASSIGNED,
                DeliveryStatus.RESCHEDULED,
                DeliveryStatus.OUT_FOR_DELIVERY,
                DeliveryStatus.DELIVERED,
                DeliveryStatus.DELIVERED_LATE,
                DeliveryStatus.FAILED,
                DeliveryStatus.CANCELLED
        ));

        // From DELIVERY_FAILED or FAILED
        Set<DeliveryStatus> fromFailed = EnumSet.of(
                DeliveryStatus.DELIVERY_FAILED,
                DeliveryStatus.FAILED,
                DeliveryStatus.READY_FOR_DELIVERY,
                DeliveryStatus.ASSIGNED,
                DeliveryStatus.RESCHEDULED,
                DeliveryStatus.OUT_FOR_DELIVERY,
                DeliveryStatus.RETURNED,
                DeliveryStatus.CANCELLED
        );
        ALLOWED_TRANSITIONS.put(DeliveryStatus.DELIVERY_FAILED, fromFailed);
        ALLOWED_TRANSITIONS.put(DeliveryStatus.FAILED, fromFailed);

        // Terminal states: DELIVERED, DELIVERED_ON_TIME, DELIVERED_LATE, CANCELLED, RETURNED (only self / no-op)
        ALLOWED_TRANSITIONS.put(DeliveryStatus.DELIVERED, EnumSet.of(DeliveryStatus.DELIVERED, DeliveryStatus.DELIVERED_ON_TIME, DeliveryStatus.DELIVERED_LATE));
        ALLOWED_TRANSITIONS.put(DeliveryStatus.DELIVERED_ON_TIME, EnumSet.of(DeliveryStatus.DELIVERED_ON_TIME));
        ALLOWED_TRANSITIONS.put(DeliveryStatus.DELIVERED_LATE, EnumSet.of(DeliveryStatus.DELIVERED_LATE));
        ALLOWED_TRANSITIONS.put(DeliveryStatus.CANCELLED, EnumSet.of(DeliveryStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(DeliveryStatus.RETURNED, EnumSet.of(DeliveryStatus.RETURNED));
    }

    public DeliveryTrackingService(DeliveryRepository deliveryRepository,
                                   DeliveryStatusHistoryRepository historyRepository,
                                   OrderRepository orderRepository,
                                   UserRepository userRepository) {
        this.deliveryRepository = deliveryRepository;
        this.historyRepository = historyRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    /** Record a status change in delivery_status_history and update the delivery record */
    @Transactional
    public DeliveryStatusHistory recordStatusChange(Delivery delivery, DeliveryStatus newStatus, String message, String updatedBy) {
        DeliveryStatus currentStatus = delivery.getDeliveryStatus();

        // Idempotent check: if status is unchanged and history already exists, avoid duplicate recording unless message is provided
        if (currentStatus == newStatus) {
            List<DeliveryStatusHistory> existing = historyRepository.findByDeliveryIdOrderByCreatedAtAscIdAsc(delivery.getId());
            if (!existing.isEmpty() && existing.get(existing.size() - 1).getStatus() == newStatus && (message == null || message.isBlank())) {
                return existing.get(existing.size() - 1);
            }
        }

        validateTransition(currentStatus, newStatus);

        delivery.setDeliveryStatus(newStatus);
        boolean isDeliveredState = newStatus == DeliveryStatus.DELIVERED || newStatus == DeliveryStatus.DELIVERED_ON_TIME || newStatus == DeliveryStatus.DELIVERED_LATE;
        if (isDeliveredState && delivery.getCompletedAt() == null) {
            delivery.setCompletedAt(OffsetDateTime.now());
        }

        // Align order status
        Order order = delivery.getOrder();
        if (newStatus == DeliveryStatus.OUT_FOR_DELIVERY && order.getStatus() != OrderStatus.OUT_FOR_DELIVERY) {
            order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
            orderRepository.save(order);
        } else if (isDeliveredState && order.getStatus() != OrderStatus.DELIVERED) {
            order.setStatus(OrderStatus.DELIVERED);
            orderRepository.save(order);
        } else if (newStatus == DeliveryStatus.CANCELLED && order.getStatus() != OrderStatus.CANCELLED) {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
        }

        deliveryRepository.save(delivery);

        String actorName = (updatedBy != null && !updatedBy.isBlank()) ? updatedBy.trim() : "System";
        String statusMsg = (message != null && !message.isBlank()) ? message.trim() : getDefaultMessageForStatus(newStatus);

        DeliveryStatusHistory history = new DeliveryStatusHistory(delivery, order, newStatus, statusMsg, actorName);
        // Keep both sides in sync so reports in this transaction see the new event too.
        delivery.getStatusHistory().add(history);
        return historyRepository.save(history);
    }

    public void validateTransition(DeliveryStatus currentStatus, DeliveryStatus targetStatus) {
        if (currentStatus == targetStatus) {
            return;
        }
        Set<DeliveryStatus> allowed = ALLOWED_TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(targetStatus)) {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                    String.format("Invalid status transition from '%s' to '%s'.", currentStatus, targetStatus));
        }
    }

    @Transactional(readOnly = true)
    public DeliveryTrackingDto getCustomerOrderTracking(Long orderId) {
        User customer = getAuthenticatedUser();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (!order.getUser().getId().equals(customer.getId())) {
            throw BusinessException.forbidden("Order does not belong to your account.");
        }

        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", "orderId", orderId));

        return buildTrackingDto(delivery);
    }

    @Transactional
    public DeliveryTrackingDto updateDeliveryStatusByStaff(Long deliveryId, DeliveryStatus newStatus, String message) {
        User staff = getAuthenticatedUser();
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", "id", deliveryId));

        boolean isStaff = staff.getRoles().contains(Role.DELIVERY_STAFF);
        boolean isCoordinatorOrManager = staff.getRoles().contains(Role.DELIVERY_COORDINATOR) || staff.getRoles().contains(Role.MANAGER);

        if (!isCoordinatorOrManager) {
            if (!isStaff) {
                throw BusinessException.forbidden("You are not authorized to update delivery status.");
            }
            if (delivery.getAssignedStaff() == null || !delivery.getAssignedStaff().getId().equals(staff.getId())) {
                throw BusinessException.forbidden("Delivery is not assigned to you.");
            }
        }

        recordStatusChange(delivery, newStatus, message, staff.getFullName() + " (" + staff.getEmail() + ")");
        return buildTrackingDto(deliveryRepository.findById(deliveryId).orElse(delivery));
    }

    @Transactional(readOnly = true)
    public DeliveryTrackingDto buildTrackingDto(Delivery delivery) {
        List<DeliveryStatusHistory> historyEntities = historyRepository.findByDeliveryIdOrderByCreatedAtAscIdAsc(delivery.getId());
        List<DeliveryStatusHistoryDto> historyDtos = historyEntities.stream()
                .map(DeliveryStatusHistoryDto::from)
                .toList();

        OffsetDateTime lastUpdated = historyEntities.isEmpty()
                ? (delivery.getUpdatedAt() != null ? delivery.getUpdatedAt() : delivery.getCreatedAt())
                : historyEntities.get(historyEntities.size() - 1).getCreatedAt();

        String driverName = delivery.getAssignedStaff() != null ? delivery.getAssignedStaff().getFullName() : null;
        String driverPhone = delivery.getAssignedStaff() != null ? delivery.getAssignedStaff().getPhone() : null;

        Order order = delivery.getOrder();
        String fullAddress = String.format("%s, %s %s", order.getDeliveryStreet(), order.getDeliveryCity(), order.getDeliveryPostalCode());

        return new DeliveryTrackingDto(
                order.getId(),
                order.getOrderNumber(),
                delivery.getId(),
                delivery.getDeliveryStatus(),
                delivery.getScheduledDate(),
                delivery.getScheduledSlot(),
                driverName,
                driverPhone,
                fullAddress,
                delivery.getFailureReason(),
                lastUpdated,
                delivery.getCompletedAt(),
                historyDtos
        );
    }

    private String getDefaultMessageForStatus(DeliveryStatus status) {
        return switch (status) {
            case ORDER_CONFIRMED -> "Order confirmed and received";
            case PROCESSING -> "Order is being processed and packaged";
            case READY_FOR_DELIVERY -> "Order is ready for dispatch";
            case ASSIGNED -> "Delivery driver assigned";
            case OUT_FOR_DELIVERY -> "Package is out for delivery";
            case DELIVERED -> "Delivered to customer";
            case DELIVERED_ON_TIME -> "Delivered on time to customer";
            case DELIVERED_LATE -> "Delivered late to customer";
            case OVERDUE -> "Delivery slot time passed; marked as Overdue";
            case RESCHEDULED -> "Delivery rescheduled to new time slot";
            case SCHEDULED -> "Delivery scheduled";
            case DELIVERY_FAILED, FAILED -> "Delivery attempt failed";
            case CANCELLED -> "Delivery cancelled";
            case RETURNED -> "Package returned to distribution center";
            case PENDING -> "Order delivery pending";
        };
    }
}
