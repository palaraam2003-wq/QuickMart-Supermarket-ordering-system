package com.rapidmart.delivery.service;

import com.rapidmart.common.enums.*;
import com.rapidmart.common.exception.BusinessException;
import com.rapidmart.common.exception.ResourceNotFoundException;
import com.rapidmart.delivery.dto.DelayedDeliveryDto;
import com.rapidmart.delivery.dto.DeliveryDto;
import com.rapidmart.delivery.dto.DeliveryStaffSummaryDto;
import com.rapidmart.delivery.dto.DriverSlotLimitDto;
import com.rapidmart.delivery.entity.CodSettlement;
import com.rapidmart.delivery.entity.Delivery;
import com.rapidmart.delivery.entity.DeliveryAttempt;
import com.rapidmart.delivery.entity.DeliveryStatusHistory;
import com.rapidmart.delivery.entity.DriverSlotLimit;
import com.rapidmart.delivery.repository.CodSettlementRepository;
import com.rapidmart.delivery.repository.DeliveryAttemptRepository;
import com.rapidmart.delivery.repository.DeliveryRepository;
import com.rapidmart.delivery.repository.DriverSlotLimitRepository;
import com.rapidmart.delivery.util.SlotUtils;
import com.rapidmart.loyalty.service.LoyaltyService;
import com.rapidmart.order.entity.Order;
import com.rapidmart.order.repository.OrderRepository;
import com.rapidmart.order.service.OrderService;
import com.rapidmart.payment.entity.Payment;
import com.rapidmart.payment.repository.PaymentRepository;
import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DeliveryService {

    private static final Set<DeliveryStatus> ASSIGNABLE = EnumSet.of(
            DeliveryStatus.ORDER_CONFIRMED,
            DeliveryStatus.PENDING,
            DeliveryStatus.SCHEDULED,
            DeliveryStatus.ASSIGNED,
            DeliveryStatus.RESCHEDULED,
            DeliveryStatus.OVERDUE,
            DeliveryStatus.FAILED,
            DeliveryStatus.DELIVERY_FAILED
    );

    private static final Set<DeliveryStatus> ACTIVE_DRIVER_ASSIGNMENTS = EnumSet.of(
            DeliveryStatus.ASSIGNED,
            DeliveryStatus.RESCHEDULED,
            DeliveryStatus.OUT_FOR_DELIVERY,
            DeliveryStatus.OVERDUE
    );

    /** Upper bound for a single driver's orders in one time slot; guards against typos such as 300 instead of 3. */
    private static final int MAX_ORDERS_PER_SLOT = 50;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final CodSettlementRepository codSettlementRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final LoyaltyService loyaltyService;
    private final OrderService orderService;
    private final DeliveryTrackingService deliveryTrackingService;
    private final DriverSlotLimitRepository driverSlotLimitRepository;

    public DeliveryService(DeliveryRepository deliveryRepository,
                           DeliveryAttemptRepository deliveryAttemptRepository,
                           CodSettlementRepository codSettlementRepository,
                           PaymentRepository paymentRepository,
                           UserRepository userRepository,
                           OrderRepository orderRepository,
                           LoyaltyService loyaltyService,
                           OrderService orderService,
                           DeliveryTrackingService deliveryTrackingService,
                           DriverSlotLimitRepository driverSlotLimitRepository) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.codSettlementRepository = codSettlementRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.loyaltyService = loyaltyService;
        this.orderService = orderService;
        this.deliveryTrackingService = deliveryTrackingService;
        this.driverSlotLimitRepository = driverSlotLimitRepository;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private Delivery findDelivery(Long deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", "id", deliveryId));
    }

    private Payment paymentFor(Order order) {
        return paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new BusinessException("NO_PAYMENT", "Order has no payment record."));
    }

    /** Delivery staff may only act on deliveries assigned to them. */
    private Delivery findOwnDelivery(Long deliveryId) {
        User staff = getAuthenticatedUser();
        Delivery delivery = findDelivery(deliveryId);
        if (delivery.getAssignedStaff() == null || !delivery.getAssignedStaff().getId().equals(staff.getId())) {
            throw BusinessException.forbidden("Delivery is not assigned to you.");
        }
        return delivery;
    }

    public DeliveryDto toDto(Delivery delivery) {
        return DeliveryDto.from(delivery, paymentRepository.findByOrderId(delivery.getOrder().getId()).orElse(null));
    }

    // ---------------------------------------------------------------- Driver Order Limits (Time Slot)

    @Transactional
    public DriverSlotLimitDto setDriverSlotLimit(Long driverId, LocalDate scheduledDate, String scheduledSlot, Integer maxOrders) {
        if (driverId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DRIVER_REQUIRED", "Driver is required.");
        }
        if (scheduledDate == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DATE_REQUIRED", "Scheduled date is required.");
        }
        if (scheduledDate.isBefore(LocalDate.now())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE_DATE", "Order limits cannot be set for a past date.");
        }
        if (scheduledSlot == null || scheduledSlot.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SLOT_REQUIRED", "Time slot is required.");
        }
        if (!SlotUtils.isKnownSlot(scheduledSlot)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SLOT", "Unknown delivery time slot: " + scheduledSlot + ".");
        }
        if (maxOrders == null || maxOrders < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_MAX_ORDERS", "Max orders must be a non-negative integer.");
        }
        if (maxOrders > MAX_ORDERS_PER_SLOT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_MAX_ORDERS", "Max orders cannot exceed " + MAX_ORDERS_PER_SLOT + " per time slot.");
        }

        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", driverId));

        if (!driver.getRoles().contains(Role.DELIVERY_STAFF)) {
            throw new BusinessException("INVALID_ROLE", "User is not delivery staff.");
        }
        if (!driver.isActive()) {
            throw new BusinessException("STAFF_INACTIVE", "Assigned delivery staff account is deactivated.");
        }

        String slot = scheduledSlot.trim().toUpperCase();
        DriverSlotLimit limit = driverSlotLimitRepository.findByDriverIdAndScheduledDateAndScheduledSlot(driverId, scheduledDate, slot)
                .orElseGet(() -> new DriverSlotLimit(driver, scheduledDate, slot, maxOrders));

        limit.setMaxOrders(maxOrders);
        DriverSlotLimit saved = driverSlotLimitRepository.save(limit);

        long assignedCount = countActiveDriverAssignments(driverId, scheduledDate, slot, null);
        return DriverSlotLimitDto.from(saved, assignedCount);
    }

    @Transactional(readOnly = true)
    public List<DriverSlotLimitDto> getDriverSlotLimits(LocalDate scheduledDate, String scheduledSlot, Long driverId) {
        List<DriverSlotLimit> limits;
        if (scheduledDate != null && scheduledSlot != null && !scheduledSlot.isBlank()) {
            limits = driverSlotLimitRepository.findByScheduledDateAndScheduledSlot(scheduledDate, scheduledSlot.trim());
        } else if (scheduledDate != null) {
            limits = driverSlotLimitRepository.findByScheduledDate(scheduledDate);
        } else if (driverId != null) {
            limits = driverSlotLimitRepository.findByDriverId(driverId);
        } else {
            limits = driverSlotLimitRepository.findAll();
        }

        if (driverId != null) {
            limits = limits.stream().filter(l -> l.getDriver().getId().equals(driverId)).toList();
        }

        return limits.stream().map(limit -> {
            long assigned = countActiveDriverAssignments(limit.getDriver().getId(), limit.getScheduledDate(), limit.getScheduledSlot(), null);
            return DriverSlotLimitDto.from(limit, assigned);
        }).sorted(Comparator.comparing(DriverSlotLimitDto::scheduledDate)
                .thenComparingInt(dto -> SlotUtils.slotOrder(dto.scheduledSlot()))
                .thenComparing(DriverSlotLimitDto::driverName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    /** Removes a limit. Existing assignments stay; the driver simply cannot take new orders in that slot until a limit is set again. */
    @Transactional
    public void deleteDriverSlotLimit(Long limitId) {
        DriverSlotLimit limit = driverSlotLimitRepository.findById(limitId)
                .orElseThrow(() -> new ResourceNotFoundException("DriverSlotLimit", "id", limitId));
        driverSlotLimitRepository.delete(limit);
    }

    public long countActiveDriverAssignments(Long driverId, LocalDate date, String slot, Long excludeDeliveryId) {
        return deliveryRepository.countDriverSlotAssignments(driverId, date, slot, ACTIVE_DRIVER_ASSIGNMENTS, excludeDeliveryId);
    }

    // ---------------------------------------------------------------- Overdue & Delayed Deliveries

    @Transactional
    public List<Delivery> checkAndUpdateOverdueDeliveries() {
        LocalDateTime now = LocalDateTime.now();
        List<Delivery> allDeliveries = deliveryRepository.findAll();
        List<Delivery> updated = new ArrayList<>();

        for (Delivery delivery : allDeliveries) {
            DeliveryStatus status = delivery.getDeliveryStatus();
            if (status == DeliveryStatus.ASSIGNED || status == DeliveryStatus.OUT_FOR_DELIVERY
                    || status == DeliveryStatus.RESCHEDULED || status == DeliveryStatus.SCHEDULED || status == DeliveryStatus.PENDING) {
                if (SlotUtils.isSlotPassed(delivery.getScheduledDate(), delivery.getScheduledSlot(), now)) {
                    deliveryTrackingService.recordStatusChange(delivery, DeliveryStatus.OVERDUE, "Delivery window passed; marked as Overdue", "System");
                    updated.add(delivery);
                }
            }
        }
        return updated;
    }

    @Transactional(readOnly = true)
    public List<DelayedDeliveryDto> getDelayedDeliveries() {
        List<Delivery> deliveries = deliveryRepository.findAll();
        List<DelayedDeliveryDto> delayed = new ArrayList<>();
        ZoneId zoneId = ZoneId.systemDefault();

        for (Delivery delivery : deliveries) {
            DeliveryStatus status = delivery.getDeliveryStatus();
            boolean isDelayedStatus = status == DeliveryStatus.DELIVERED_LATE;
            boolean wasOverdueInHistory = false;

            if (!isDelayedStatus && (status == DeliveryStatus.DELIVERED || status == DeliveryStatus.DELIVERED_ON_TIME)) {
                List<DeliveryStatusHistory> history = delivery.getStatusHistory();
                wasOverdueInHistory = history.stream().anyMatch(h -> h.getStatus() == DeliveryStatus.OVERDUE);
            }

            LocalDateTime expectedEndLocal = SlotUtils.getSlotEndDateTime(delivery.getScheduledDate(), delivery.getScheduledSlot());
            OffsetDateTime expectedEndTime = expectedEndLocal.atZone(zoneId).toOffsetDateTime();
            OffsetDateTime actualTime = delivery.getCompletedAt();

            boolean isTimeLate = actualTime != null && actualTime.toLocalDateTime().isAfter(expectedEndLocal);

            if (isDelayedStatus || wasOverdueInHistory || isTimeLate) {
                if (actualTime != null) {
                    long delayMinutes = Math.max(0, Duration.between(expectedEndTime, actualTime).toMinutes());
                    String formattedDuration = formatDuration(delayMinutes);
                    User staff = delivery.getAssignedStaff();
                    Order order = delivery.getOrder();

                    delayed.add(new DelayedDeliveryDto(
                            delivery.getId(),
                            order.getId(),
                            order.getOrderNumber(),
                            staff != null ? staff.getId() : null,
                            staff != null ? staff.getFullName() : "Unassigned",
                            staff != null ? staff.getEmail() : null,
                            delivery.getScheduledDate(),
                            delivery.getScheduledSlot(),
                            expectedEndTime,
                            actualTime,
                            delayMinutes,
                            formattedDuration,
                            status == DeliveryStatus.DELIVERED_LATE ? DeliveryStatus.DELIVERED_LATE : DeliveryStatus.DELIVERED,
                            order.getTotalAmount(),
                            order.getUser() != null ? order.getUser().getFullName() : null,
                            order.getDeliveryCity()
                    ));
                }
            }
        }
        return delayed.stream()
                .sorted(Comparator.comparing(DelayedDeliveryDto::actualDeliveryTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private String formatDuration(long minutes) {
        if (minutes <= 0) return "0 mins";
        long hours = minutes / 60;
        long mins = minutes % 60;
        if (hours > 0) {
            return String.format("%d hr%s %d min%s", hours, hours > 1 ? "s" : "", mins, mins != 1 ? "s" : "");
        }
        return String.format("%d min%s", mins, mins != 1 ? "s" : "");
    }

    // ---------------------------------------------------------------- Coordinator Actions

    @Transactional
    public List<DeliveryDto> getAllDeliveries(DeliveryStatus status) {
        checkAndUpdateOverdueDeliveries();
        List<Delivery> deliveries = status != null
                ? deliveryRepository.findByDeliveryStatus(status)
                : deliveryRepository.findAllByOrderByScheduledDateAscIdAsc();
        return deliveries.stream().map(this::toDto).toList();
    }

    /** Driver directory with optional search, account-state, and schedule-availability filters. */
    @Transactional(readOnly = true)
    public List<DeliveryStaffSummaryDto> getDeliveryStaff(String search,
                                                          Boolean active,
                                                          LocalDate availabilityDate,
                                                          String availabilitySlot,
                                                          Boolean available) {
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String slot = availabilitySlot == null || availabilitySlot.isBlank() ? null : availabilitySlot.trim();
        Map<Long, List<Delivery>> assignmentsByStaff = deliveryRepository.findAll().stream()
                .filter(d -> d.getAssignedStaff() != null)
                .collect(Collectors.groupingBy(d -> d.getAssignedStaff().getId()));

        return userRepository.findByRolesContaining(Role.DELIVERY_STAFF).stream()
                .filter(user -> active == null || user.isActive() == active)
                .filter(user -> query.isEmpty()
                        || user.getFullName().toLowerCase(Locale.ROOT).contains(query)
                        || user.getEmail().toLowerCase(Locale.ROOT).contains(query)
                        || (user.getPhone() != null && user.getPhone().toLowerCase(Locale.ROOT).contains(query)))
                .map(user -> toDeliveryStaffSummary(user,
                        assignmentsByStaff.getOrDefault(user.getId(), List.of()), availabilityDate, slot))
                .filter(summary -> available == null || summary.available() == available)
                .sorted(Comparator.comparing(DeliveryStaffSummaryDto::fullName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(DeliveryStaffSummaryDto::id))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeliveryDto> getDeliveryStaffAssignments(Long staffId) {
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", staffId));
        if (!staff.getRoles().contains(Role.DELIVERY_STAFF)) {
            throw new BusinessException("INVALID_ROLE", "User is not delivery staff.");
        }
        return deliveryRepository.findByAssignedStaffId(staffId).stream()
                .sorted(Comparator.comparing(Delivery::getScheduledDate, Comparator.reverseOrder())
                        .thenComparing(Delivery::getId, Comparator.reverseOrder()))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public void assertNoActiveAssignments(Long staffId) {
        boolean hasActiveAssignments = deliveryRepository.findByAssignedStaffId(staffId).stream()
                .anyMatch(delivery -> ACTIVE_DRIVER_ASSIGNMENTS.contains(delivery.getDeliveryStatus()));
        if (hasActiveAssignments) {
            throw new BusinessException("STAFF_HAS_ACTIVE_ASSIGNMENTS",
                    "Reassign the driver's active deliveries before deactivating the account.");
        }
    }

    private DeliveryStaffSummaryDto toDeliveryStaffSummary(User user,
                                                           List<Delivery> assignments,
                                                           LocalDate availabilityDate,
                                                           String availabilitySlot) {
        long activeAssignments = assignments.stream()
                .filter(delivery -> ACTIVE_DRIVER_ASSIGNMENTS.contains(delivery.getDeliveryStatus()))
                .count();

        boolean scheduleConflict = false;
        if (availabilityDate != null && availabilitySlot != null && !availabilitySlot.isBlank()) {
            Optional<DriverSlotLimit> limitOpt = driverSlotLimitRepository.findByDriverIdAndScheduledDateAndScheduledSlot(user.getId(), availabilityDate, availabilitySlot.trim());
            if (limitOpt.isPresent()) {
                long currentAssigned = countActiveDriverAssignments(user.getId(), availabilityDate, availabilitySlot.trim(), null);
                scheduleConflict = currentAssigned >= limitOpt.get().getMaxOrders();
            } else {
                scheduleConflict = assignments.stream().anyMatch(delivery ->
                        availabilityDate.equals(delivery.getScheduledDate())
                                && availabilitySlot.trim().equalsIgnoreCase(delivery.getScheduledSlot())
                                && ACTIVE_DRIVER_ASSIGNMENTS.contains(delivery.getDeliveryStatus()));
            }
        } else if (availabilityDate != null) {
            scheduleConflict = assignments.stream().anyMatch(delivery ->
                    availabilityDate.equals(delivery.getScheduledDate())
                            && ACTIVE_DRIVER_ASSIGNMENTS.contains(delivery.getDeliveryStatus()));
        } else {
            scheduleConflict = assignments.stream()
                    .anyMatch(delivery -> delivery.getDeliveryStatus() == DeliveryStatus.OUT_FOR_DELIVERY);
        }

        return new DeliveryStaffSummaryDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.isActive(),
                user.getRoles().equals(Set.of(Role.DELIVERY_STAFF)),
                user.isActive() && !scheduleConflict,
                activeAssignments,
                assignments.size(),
                availabilityDate,
                availabilitySlot,
                user.getCreatedAt()
        );
    }

    /**
     * Assigns (or reschedules) a delivery.
     * Enforces time slot validation, driver availability, duplicate assignments, and driver order limits.
     */
    @Transactional
    public Delivery assignDeliveryStaff(Long deliveryId, Long staffId, LocalDate scheduledDate, String scheduledSlot) {
        Delivery delivery = findDelivery(deliveryId);

        if (!ASSIGNABLE.contains(delivery.getDeliveryStatus())) {
            throw new BusinessException("INVALID_DELIVERY_STATE", "Delivery cannot be assigned in status " + delivery.getDeliveryStatus() + ".");
        }
        if (scheduledDate == null || scheduledDate.isBefore(LocalDate.now())) {
            throw new BusinessException("INVALID_SCHEDULE_DATE", "Scheduled date cannot be in the past.");
        }
        if (scheduledSlot == null || scheduledSlot.isBlank()) {
            throw new BusinessException("INVALID_SLOT", "A delivery slot is required.");
        }
        if (!SlotUtils.isValidSlot(scheduledSlot)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SLOT", "Invalid delivery time slot.");
        }

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", staffId));

        if (!staff.getRoles().contains(Role.DELIVERY_STAFF)) {
            throw new BusinessException("INVALID_ROLE", "Assigned user is not delivery staff.");
        }
        if (!staff.isActive()) {
            throw new BusinessException("STAFF_INACTIVE", "Assigned delivery staff account is deactivated.");
        }

        Order order = delivery.getOrder();
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new BusinessException("INVALID_ORDER_STATE", "Order in status " + order.getStatus() + " cannot be scheduled.");
        }

        Payment payment = paymentFor(order);
        if (payment.getPaymentMethod() != PaymentMethod.COD && payment.getPaymentStatus() != PaymentStatus.PAID) {
            throw new BusinessException("UNPAID_ORDER", "Unpaid non-COD orders cannot be assigned for delivery.");
        }

        String slot = scheduledSlot.trim();

        // 1. Missing Limit Validation (Requirement 7)
        Optional<DriverSlotLimit> limitOpt = driverSlotLimitRepository.findByDriverIdAndScheduledDateAndScheduledSlot(staffId, scheduledDate, slot);
        if (limitOpt.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MISSING_LIMIT", "No order limit is set for this driver on " + scheduledDate + " (" + slot + "). Set one under Driver Order Limits by Time Slot first.");
        }
        DriverSlotLimit slotLimit = limitOpt.get();

        // 2. Limit Exceeded Validation (Requirement 2 & 7)
        long currentAssigned = countActiveDriverAssignments(staffId, scheduledDate, slot, deliveryId);
        if (currentAssigned >= slotLimit.getMaxOrders()) {
            throw new BusinessException(HttpStatus.CONFLICT, "LIMIT_EXCEEDED",
                    "Driver has reached maximum order limit (" + slotLimit.getMaxOrders() + ") for this time slot.");
        }

        // 3. Duplicate Assignment Validation (Requirement 7)
        if (delivery.getAssignedStaff() != null && delivery.getAssignedStaff().getId().equals(staffId)
                && scheduledDate.equals(delivery.getScheduledDate())
                && slot.equalsIgnoreCase(delivery.getScheduledSlot())
                && (delivery.getDeliveryStatus() == DeliveryStatus.ASSIGNED || delivery.getDeliveryStatus() == DeliveryStatus.RESCHEDULED)) {
            throw new BusinessException(HttpStatus.CONFLICT, "DUPLICATE_ASSIGNMENT", "Delivery is already assigned to this driver for the selected date and time slot.");
        }

        boolean reschedule = delivery.getDeliveryStatus() != DeliveryStatus.PENDING
                && delivery.getDeliveryStatus() != DeliveryStatus.ORDER_CONFIRMED
                && delivery.getDeliveryStatus() != DeliveryStatus.SCHEDULED;
        DeliveryStatus newStatus = reschedule ? DeliveryStatus.RESCHEDULED : DeliveryStatus.ASSIGNED;

        delivery.setAssignedStaff(staff);
        delivery.setScheduledDate(scheduledDate);
        delivery.setScheduledSlot(slot);
        delivery.setDeliveryStatus(newStatus);
        delivery.setReturnedItemsChecked(false);

        User coordinator = getAuthenticatedUser();
        String actorName = coordinator != null ? coordinator.getFullName() : "Coordinator";
        String note = (reschedule ? "Rescheduled" : "Assigned") + " to " + staff.getFullName() + " on " + scheduledDate + " (" + slot + ")";

        deliveryAttemptRepository.save(new DeliveryAttempt(delivery, newStatus, note));
        deliveryRepository.save(delivery);

        deliveryTrackingService.recordStatusChange(delivery, newStatus, note, actorName);
        return delivery;
    }

    /** After a FAILED delivery the coordinator confirms the returned items were checked back in. */
    @Transactional
    public Delivery markReturnedItemsChecked(Long deliveryId) {
        Delivery delivery = findDelivery(deliveryId);
        if (delivery.getDeliveryStatus() != DeliveryStatus.FAILED) {
            throw new BusinessException("INVALID_DELIVERY_STATE", "Returned items can only be checked for failed deliveries.");
        }
        if (!delivery.isReturnedItemsChecked()) {
            delivery.setReturnedItemsChecked(true);
            deliveryAttemptRepository.save(new DeliveryAttempt(delivery, DeliveryStatus.FAILED, "Returned items checked by coordinator"));
        }
        return deliveryRepository.save(delivery);
    }

    /** Coordinator cancellation: only for FAILED deliveries whose returned items were checked. Idempotent. */
    @Transactional
    public Delivery cancelFailedDelivery(Long deliveryId, String reason) {
        User coordinator = getAuthenticatedUser();
        Delivery delivery = findDelivery(deliveryId);

        if (delivery.getDeliveryStatus() == DeliveryStatus.CANCELLED) {
            return delivery;
        }
        if (delivery.getDeliveryStatus() != DeliveryStatus.FAILED) {
            throw new BusinessException("INVALID_DELIVERY_STATE", "Only failed deliveries can be cancelled by a coordinator.");
        }
        if (!delivery.isReturnedItemsChecked()) {
            throw new BusinessException("RETURNED_ITEMS_NOT_CHECKED", "Check the returned items before cancelling the order.");
        }

        String cancelReason = reason == null || reason.isBlank() ? "Cancelled after failed delivery" : reason.trim();
        orderService.cancelAndRestore(delivery.getOrder(), coordinator, cancelReason);
        deliveryAttemptRepository.save(new DeliveryAttempt(delivery, DeliveryStatus.CANCELLED, cancelReason));
        return deliveryRepository.findById(deliveryId).orElseThrow();
    }

    // ---------------------------------------------------------------- Delivery staff Actions

    @Transactional(readOnly = true)
    public List<DeliveryDto> getMyDeliveries() {
        User staff = getAuthenticatedUser();
        return deliveryRepository.findByAssignedStaffId(staff.getId()).stream()
                .sorted((a, b) -> a.getScheduledDate().compareTo(b.getScheduledDate()))
                .map(this::toDto)
                .toList();
    }

    /** ASSIGNED / RESCHEDULED -> OUT_FOR_DELIVERY */
    @Transactional
    public Delivery markOutForDelivery(Long deliveryId) {
        Delivery delivery = findOwnDelivery(deliveryId);

        if (delivery.getDeliveryStatus() == DeliveryStatus.OUT_FOR_DELIVERY) {
            return delivery;
        }
        if (delivery.getDeliveryStatus() != DeliveryStatus.ASSIGNED && delivery.getDeliveryStatus() != DeliveryStatus.RESCHEDULED) {
            throw new BusinessException("INVALID_DELIVERY_STATE", "Only assigned or rescheduled deliveries can be taken out for delivery.");
        }
        Order order = delivery.getOrder();
        if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.OUT_FOR_DELIVERY) {
            throw new BusinessException("INVALID_ORDER_STATE", "Order in status " + order.getStatus() + " cannot go out for delivery.");
        }

        delivery.setDeliveryStatus(DeliveryStatus.OUT_FOR_DELIVERY);
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        orderRepository.save(order);

        deliveryAttemptRepository.save(new DeliveryAttempt(delivery, DeliveryStatus.OUT_FOR_DELIVERY, "Package taken out for delivery"));
        deliveryRepository.save(delivery);

        User staff = getAuthenticatedUser();
        deliveryTrackingService.recordStatusChange(delivery, DeliveryStatus.OUT_FOR_DELIVERY, "Package taken out for delivery", staff.getFullName() + " (" + staff.getEmail() + ")");
        return delivery;
    }

    /** OUT_FOR_DELIVERY -> FAILED (reason required). */
    @Transactional
    public Delivery recordFailedDelivery(Long deliveryId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("REASON_REQUIRED", "Nonblank reason is required for failed delivery.");
        }

        Delivery delivery = findOwnDelivery(deliveryId);

        if (delivery.getDeliveryStatus() != DeliveryStatus.OUT_FOR_DELIVERY && delivery.getDeliveryStatus() != DeliveryStatus.OVERDUE) {
            throw new BusinessException("INVALID_DELIVERY_STATE", "Only deliveries that are out for delivery or overdue can be marked as failed.");
        }

        delivery.setDeliveryStatus(DeliveryStatus.FAILED);
        delivery.setFailureReason(reason.trim());
        delivery.setReturnedItemsChecked(false);

        Order order = delivery.getOrder();
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        deliveryAttemptRepository.save(new DeliveryAttempt(delivery, DeliveryStatus.FAILED, reason.trim()));
        deliveryRepository.save(delivery);

        User staff = getAuthenticatedUser();
        deliveryTrackingService.recordStatusChange(delivery, DeliveryStatus.FAILED, reason.trim(), staff.getFullName() + " (" + staff.getEmail() + ")");
        return delivery;
    }

    /**
     * Completes a delivery. Checks whether delivery was delivered on time or late.
     */
    @Transactional
    public Delivery completeDelivery(Long deliveryId, BigDecimal collectedAmount) {
        Delivery delivery = findOwnDelivery(deliveryId);

        if (delivery.getDeliveryStatus() == DeliveryStatus.DELIVERED
                || delivery.getDeliveryStatus() == DeliveryStatus.DELIVERED_ON_TIME
                || delivery.getDeliveryStatus() == DeliveryStatus.DELIVERED_LATE) {
            return delivery; // Idempotent
        }
        if (delivery.getDeliveryStatus() != DeliveryStatus.OUT_FOR_DELIVERY && delivery.getDeliveryStatus() != DeliveryStatus.OVERDUE) {
            throw new BusinessException("INVALID_DELIVERY_STATE", "Only active deliveries can be completed.");
        }

        Order order = delivery.getOrder();
        Payment payment = paymentFor(order);

        if (payment.getPaymentMethod() == PaymentMethod.COD) {
            if (collectedAmount == null || collectedAmount.compareTo(order.getTotalAmount()) != 0) {
                throw new BusinessException("COD_AMOUNT_MISMATCH",
                        "Collect exactly LKR " + order.getTotalAmount().toPlainString() + " before completing this COD delivery.");
            }
            if (payment.getPaymentStatus() == PaymentStatus.PENDING) {
                payment.setNotes("COD cash collected by " + delivery.getAssignedStaff().getEmail() + "; awaiting cashier verification");
                paymentRepository.save(payment);
            }
            if (codSettlementRepository.findByDeliveryId(deliveryId).isEmpty()) {
                codSettlementRepository.save(new CodSettlement(delivery, delivery.getAssignedStaff(), order.getTotalAmount()));
            }
        } else if (payment.getPaymentStatus() != PaymentStatus.PAID) {
            throw new BusinessException("UNPAID_ORDER", "Order payment is not completed; delivery cannot be finished.");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expectedEnd = SlotUtils.getSlotEndDateTime(delivery.getScheduledDate(), delivery.getScheduledSlot());
        boolean isLate = delivery.getDeliveryStatus() == DeliveryStatus.OVERDUE || now.isAfter(expectedEnd);
        DeliveryStatus finalStatus = isLate ? DeliveryStatus.DELIVERED_LATE : DeliveryStatus.DELIVERED_ON_TIME;

        delivery.setDeliveryStatus(finalStatus);
        delivery.setCompletedAt(OffsetDateTime.now());
        deliveryRepository.save(delivery);

        order.setStatus(OrderStatus.DELIVERED);
        orderRepository.save(order);

        deliveryAttemptRepository.save(new DeliveryAttempt(delivery, finalStatus, "Delivery completed successfully (" + (isLate ? "Late" : "On Time") + ")"));

        User staff = getAuthenticatedUser();
        deliveryTrackingService.recordStatusChange(delivery, finalStatus, "Delivery completed successfully (" + (isLate ? "Late" : "On Time") + ")", staff.getFullName() + " (" + staff.getEmail() + ")");

        // Loyalty points earned ONLY after successful delivery, exactly once
        loyaltyService.earnPointsForOrder(order.getUser(), order.getTotalAmount(), order.getOrderNumber());

        return delivery;
    }
}
