package com.rapidmart.order.service;

import com.rapidmart.billing.repository.BillRepository;
import com.rapidmart.cart.entity.Cart;
import com.rapidmart.cart.entity.CartItem;
import com.rapidmart.cart.repository.CartRepository;
import com.rapidmart.common.enums.*;
import com.rapidmart.common.exception.BusinessException;
import com.rapidmart.common.exception.ResourceNotFoundException;
import com.rapidmart.customer.entity.CustomerAddress;
import com.rapidmart.customer.repository.CustomerAddressRepository;
import com.rapidmart.delivery.entity.Delivery;
import com.rapidmart.delivery.repository.DeliveryRepository;
import com.rapidmart.inventory.entity.Inventory;
import com.rapidmart.inventory.repository.InventoryRepository;
import com.rapidmart.inventory.service.InventoryService;
import com.rapidmart.loyalty.entity.Offer;
import com.rapidmart.loyalty.service.LoyaltyService;
import com.rapidmart.order.dto.CheckoutRequest;
import com.rapidmart.order.dto.OrderDto;
import com.rapidmart.order.dto.OrderItemDto;
import com.rapidmart.order.entity.Order;
import com.rapidmart.order.entity.OrderItem;
import com.rapidmart.order.repository.OrderItemRepository;
import com.rapidmart.order.repository.OrderRepository;
import com.rapidmart.payment.entity.Payment;
import com.rapidmart.payment.repository.PaymentRepository;
import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CustomerAddressRepository addressRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final PaymentRepository paymentRepository;
    private final DeliveryRepository deliveryRepository;
    private final BillRepository billRepository;
    private final UserRepository userRepository;
    private final LoyaltyService loyaltyService;
    private final com.rapidmart.delivery.service.DeliveryTrackingService deliveryTrackingService;

    @Value("${rapidmart.tax.rate-percentage:0}")
    private BigDecimal taxRatePercentage;

    @Value("${rapidmart.delivery.charge:0.00}")
    private BigDecimal deliveryChargeAmount;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        CartRepository cartRepository,
                        CustomerAddressRepository addressRepository,
                        InventoryRepository inventoryRepository,
                        InventoryService inventoryService,
                        PaymentRepository paymentRepository,
                        DeliveryRepository deliveryRepository,
                        BillRepository billRepository,
                        UserRepository userRepository,
                        LoyaltyService loyaltyService,
                        com.rapidmart.delivery.service.DeliveryTrackingService deliveryTrackingService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartRepository = cartRepository;
        this.addressRepository = addressRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryService = inventoryService;
        this.paymentRepository = paymentRepository;
        this.deliveryRepository = deliveryRepository;
        this.billRepository = billRepository;
        this.userRepository = userRepository;
        this.loyaltyService = loyaltyService;
        this.deliveryTrackingService = deliveryTrackingService;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    @Transactional
    public OrderDto checkout(CheckoutRequest request) {
        User user = getAuthenticatedUser();

        // Idempotency safeguard: replaying the same key returns the original order (never another user's)
        String idempotencyKey = request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()
                ? request.getIdempotencyKey().trim() : null;
        if (idempotencyKey != null) {
            Optional<Order> existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                if (!existing.get().getUser().getId().equals(user.getId())) {
                    throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", "This checkout key has already been used.");
                }
                return mapToDto(existing.get());
            }
        }

        if (request.getPaymentMethod() == null) {
            throw new BusinessException("INVALID_PAYMENT_METHOD", "A valid payment method is required.");
        }

        // Validate Delivery Date
        if (request.getDeliveryDate() == null || !request.getDeliveryDate().isAfter(LocalDate.now())) {
            throw new BusinessException("INVALID_DELIVERY_DATE", "Delivery date must be strictly in the future.");
        }

        // Validate Address Ownership
        CustomerAddress address = addressRepository.findByIdAndUserIdAndActiveTrue(request.getAddressId(), user.getId())
                .orElseThrow(() -> new BusinessException("INVALID_ADDRESS", "Delivery address not found or does not belong to you."));

        // Validate Cart
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException("EMPTY_CART", "Cart is empty."));

        if (cart.getItems().isEmpty()) {
            throw new BusinessException("EMPTY_CART", "Cart is empty.");
        }

        // Lock inventory rows in a stable (product id) order so concurrent checkouts cannot deadlock or oversell
        List<CartItem> cartItems = new ArrayList<>(cart.getItems());
        cartItems.sort(Comparator.comparing(item -> item.getProduct().getId()));

        Map<Long, Inventory> lockedInventory = new HashMap<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (CartItem item : cartItems) {
            if (!item.getProduct().isActive()) {
                throw new BusinessException("PRODUCT_INACTIVE",
                        String.format("Product '%s' is inactive and cannot be ordered.", item.getProduct().getName()));
            }

            Inventory inventory = inventoryRepository.findByProductIdWithLock(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", item.getProduct().getId()));

            if (inventory.getQuantity() < item.getQuantity()) {
                throw new BusinessException("INSUFFICIENT_STOCK",
                        String.format("Insufficient stock for product '%s'. Requested: %d, Available: %d",
                                item.getProduct().getName(), item.getQuantity(), inventory.getQuantity()));
            }
            lockedInventory.put(item.getProduct().getId(), inventory);

            subtotal = subtotal.add(lineTotal(item));
        }

        // Offer discount (at most one offer)
        BigDecimal offerDiscount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Long appliedOfferId = null;
        if (request.getOfferCode() != null && !request.getOfferCode().isBlank()) {
            Offer offer = loyaltyService.validateAndGetOffer(request.getOfferCode(), subtotal, user);
            if (offer != null) {
                offerDiscount = subtotal.multiply(offer.getDiscountPercentage())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                appliedOfferId = offer.getId();
            }
        }

        // Point redemption discount (1 point = LKR 1)
        int redeemPoints = request.getRedeemPoints() != null ? request.getRedeemPoints() : 0;
        if (redeemPoints < 0) {
            throw new BusinessException("INVALID_POINTS", "Redeemed points cannot be negative.");
        }
        BigDecimal pointDiscount = new BigDecimal(redeemPoints).setScale(2, RoundingMode.HALF_UP);

        BigDecimal payable = subtotal.subtract(offerDiscount).subtract(pointDiscount).setScale(2, RoundingMode.HALF_UP);

        if (payable.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_TOTAL", "Discounts cannot exceed total cart value. Payable total must remain positive.");
        }

        // Tax on the discounted merchandise value, plus a flat delivery charge; both configurable
        BigDecimal taxAmount = payable.multiply(taxRatePercentage)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal deliveryCharge = deliveryChargeAmount.setScale(2, RoundingMode.HALF_UP);
        payable = payable.add(taxAmount).add(deliveryCharge).setScale(2, RoundingMode.HALF_UP);

        // Build Order with Snapshots
        String orderNumber = "RM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        boolean simulatedInstantPayment = request.getPaymentMethod() == PaymentMethod.CARD_SIMULATION
                || request.getPaymentMethod() == PaymentMethod.PAYPAL_SIMULATION;

        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setUser(user);
        order.setStatus(request.getPaymentMethod() == PaymentMethod.COD || simulatedInstantPayment
                ? OrderStatus.CONFIRMED : OrderStatus.AWAITING_PAYMENT);
        order.setDeliveryDate(request.getDeliveryDate());
        order.setDeliverySlot(request.getDeliverySlot().trim());
        order.setDeliveryStreet(address.getStreetAddress());
        order.setDeliveryCity(address.getCity());
        order.setDeliveryPostalCode(address.getPostalCode());
        order.setDeliveryPhone(address.getPhoneContact());
        order.setSubtotal(subtotal);
        order.setOfferDiscount(offerDiscount);
        order.setTaxPercentage(taxRatePercentage != null ? taxRatePercentage.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        order.setTaxAmount(taxAmount);
        order.setDeliveryCharge(deliveryCharge);
        order.setRedeemedPoints(redeemPoints);
        order.setPointDiscount(pointDiscount);
        order.setTotalAmount(payable);
        order.setAppliedOfferId(appliedOfferId);
        order.setIdempotencyKey(idempotencyKey);
        Order savedOrder = orderRepository.save(order);

        // Deduct points against the real order number
        if (redeemPoints > 0) {
            loyaltyService.deductPointsForRedemption(user, redeemPoints, orderNumber);
        }

        // Deduct inventory stock & create Order Items + Stock Movement Logs
        for (CartItem item : cartItems) {
            Inventory inventory = lockedInventory.get(item.getProduct().getId());
            inventory.setQuantity(inventory.getQuantity() - item.getQuantity());
            inventoryRepository.save(inventory);

            inventoryService.recordMovement(
                    item.getProduct(),
                    -item.getQuantity(),
                    StockMovementType.CHECKOUT_DEDUCTION,
                    orderNumber,
                    user.getId(),
                    user.getEmail()
            );

            OrderItem orderItem = new OrderItem(
                    savedOrder,
                    item.getProduct(),
                    item.getProduct().getName(),
                    item.getProduct().getPrice(),
                    item.getQuantity(),
                    lineTotal(item)
            );
            orderItemRepository.save(orderItem);
            savedOrder.getItems().add(orderItem);
        }

        // Create Payment record. CARD/PayPal are LOCAL SIMULATIONS: no real money moves.
        PaymentStatus initialPaymentStatus = simulatedInstantPayment ? PaymentStatus.PAID : PaymentStatus.PENDING;
        String paymentNote = simulatedInstantPayment
                ? "LOCAL SIMULATION - no real card/PayPal transaction occurred"
                : "Initial checkout payment record";

        Payment payment = new Payment(
                savedOrder,
                request.getPaymentMethod(),
                initialPaymentStatus,
                payable,
                request.getPaymentMethod().name() + "-" + UUID.randomUUID().toString().substring(0, 8),
                paymentNote
        );
        paymentRepository.save(payment);

        // Create Pending Delivery record
        // Stays PENDING (the status the coordinator queue, cancellation and assignment rules work on);
        // the tracking timeline shows PENDING as "Order placed".
        Delivery delivery = new Delivery(savedOrder, request.getDeliveryDate(), request.getDeliverySlot().trim());
        deliveryRepository.save(delivery);

        deliveryTrackingService.recordStatusChange(delivery, DeliveryStatus.PENDING,
                initialPaymentStatus == PaymentStatus.PAID || request.getPaymentMethod() == PaymentMethod.COD
                        ? "Order placed and confirmed"
                        : "Order placed - waiting for payment verification",
                user.getFullName() + " (" + user.getEmail() + ")");

        // Clear Cart
        cart.getItems().clear();
        cartRepository.save(cart);

        return mapToDto(savedOrder);
    }

    private static BigDecimal lineTotal(CartItem item) {
        return item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Customer cancellation: allowed only while the order is AWAITING_PAYMENT or CONFIRMED, no driver has been
     * assigned yet, and no money has been received (paid orders must go through a refund request).
     * Repeating the call on a cancelled order is a no-op.
     */
    @Transactional
    public OrderDto cancelOrder(Long orderId, String reason) {
        User user = getAuthenticatedUser();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (!order.getUser().getId().equals(user.getId())) {
            throw BusinessException.forbidden("Order does not belong to you.");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return mapToDto(order); // Idempotent
        }

        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessException("CANNOT_CANCEL", "Order cannot be cancelled in status: " + order.getStatus());
        }

        deliveryRepository.findByOrderId(orderId).ifPresent(delivery -> {
            if (delivery.getDeliveryStatus() != DeliveryStatus.PENDING && delivery.getDeliveryStatus() != DeliveryStatus.ORDER_CONFIRMED) {
                throw new BusinessException("CANNOT_CANCEL", "Order already has a delivery assignment and can no longer be cancelled online.");
            }
        });

        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            if (payment.getPaymentStatus() == PaymentStatus.PAID) {
                throw new BusinessException("PAID_ORDER_REQUIRES_REFUND", "This order is already paid. Please submit a refund request instead.");
            }
        });

        cancelAndRestore(order, user, reason);
        return mapToDto(order);
    }

    /**
     * Cancels an order and restores stock exactly once. Used by customer cancellation, coordinator cancellation
     * after returned items are checked, and refund approval for undelivered orders.
     */
    @Transactional
    public void cancelAndRestore(Order order, User actor, String reason) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new BusinessException("CANNOT_CANCEL", "Delivered orders cannot be cancelled.");
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        for (OrderItem item : order.getItems()) {
            Inventory inventory = inventoryRepository.findByProductIdWithLock(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", item.getProduct().getId()));

            inventory.setQuantity(inventory.getQuantity() + item.getQuantity());
            inventoryRepository.save(inventory);

            inventoryService.recordMovement(
                    item.getProduct(),
                    item.getQuantity(),
                    StockMovementType.CANCELLATION_RESTORATION,
                    "CANCEL_" + order.getOrderNumber(),
                    actor.getId(),
                    actor.getEmail()
            );
        }

        loyaltyService.restoreRedeemedPoints(order.getUser(), order.getRedeemedPoints(), order.getOrderNumber());

        deliveryRepository.findByOrderId(order.getId()).ifPresent(delivery -> {
            delivery.setDeliveryStatus(DeliveryStatus.CANCELLED);
            deliveryRepository.save(delivery);
            deliveryTrackingService.recordStatusChange(delivery, DeliveryStatus.CANCELLED,
                    reason == null || reason.isBlank() ? "Order cancelled" : "Order cancelled: " + reason.trim(),
                    actor.getFullName() + " (" + actor.getEmail() + ")");
        });

        paymentRepository.findByOrderId(order.getId()).ifPresent(payment -> {
            // A bill generated before payment is voided with its order; paid/refunded bills stay for the refund trail.
            if (payment.getPaymentStatus() == PaymentStatus.PENDING || payment.getPaymentStatus() == PaymentStatus.FAILED) {
                billRepository.findByOrderIdAndStatus(order.getId(), BillStatus.ACTIVE).ifPresent(bill -> {
                    bill.setStatus(BillStatus.VOIDED);
                    bill.setVoidedAt(OffsetDateTime.now());
                    bill.setVoidedBy(actor);
                    bill.setVoidReason(truncate("Order cancelled before payment" + (reason == null || reason.isBlank() ? "" : ": " + reason)));
                    billRepository.save(bill);
                });
            }
            if (payment.getPaymentStatus() == PaymentStatus.PENDING) {
                payment.setPaymentStatus(PaymentStatus.FAILED);
                payment.setNotes(truncate("Order cancelled before payment: " + (reason == null ? "" : reason)));
                paymentRepository.save(payment);
            }
        });
    }

    private static String truncate(String value) {
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    @Transactional(readOnly = true)
    public Page<OrderDto> getMyOrders(Pageable pageable) {
        User user = getAuthenticatedUser();
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable).map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public OrderDto getOrderById(Long id) {
        User user = getAuthenticatedUser();
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        if (!order.getUser().getId().equals(user.getId()) &&
                user.getRoles().stream().noneMatch(r -> r == Role.MANAGER || r == Role.CASHIER || r == Role.DELIVERY_COORDINATOR)) {
            throw BusinessException.forbidden("You do not have access to this order.");
        }

        return mapToDto(order);
    }

    public OrderDto mapToDto(Order order) {
        OrderDto dto = new OrderDto();
        dto.setId(order.getId());
        dto.setOrderNumber(order.getOrderNumber());
        dto.setStatus(order.getStatus());
        dto.setDeliveryDate(order.getDeliveryDate());
        dto.setDeliverySlot(order.getDeliverySlot());
        dto.setDeliveryStreet(order.getDeliveryStreet());
        dto.setDeliveryCity(order.getDeliveryCity());
        dto.setDeliveryPostalCode(order.getDeliveryPostalCode());
        dto.setDeliveryPhone(order.getDeliveryPhone());
        dto.setSubtotal(order.getSubtotal());
        dto.setOfferDiscount(order.getOfferDiscount());
        dto.setRedeemedPoints(order.getRedeemedPoints());
        dto.setPointDiscount(order.getPointDiscount());
        dto.setTaxPercentage(order.getTaxPercentage());
        dto.setTaxAmount(order.getTaxAmount());
        dto.setDeliveryCharge(order.getDeliveryCharge());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setFinalized(order.isFinalized());
        dto.setFinalizedAt(order.getFinalizedAt());
        if (order.getFinalizedBy() != null) {
            dto.setFinalizedByEmail(order.getFinalizedBy().getEmail());
        }
        dto.setCreatedAt(order.getCreatedAt());

        paymentRepository.findByOrderId(order.getId()).ifPresent(p -> {
            dto.setPaymentMethod(p.getPaymentMethod());
            dto.setPaymentStatus(p.getPaymentStatus());
        });

        billRepository.findByOrderIdAndStatus(order.getId(), BillStatus.ACTIVE)
                .ifPresent(b -> dto.setBillNumber(b.getBillNumber()));

        List<OrderItemDto> itemDtos = order.getItems().stream()
                .map(item -> new OrderItemDto(
                        item.getId(),
                        item.getProduct().getId(),
                        item.getProductNameSnapshot(),
                        item.getUnitPriceSnapshot(),
                        item.getQuantity(),
                        item.getLineTotal()
                ))
                .collect(Collectors.toList());
        dto.setItems(itemDtos);

        return dto;
    }
}
