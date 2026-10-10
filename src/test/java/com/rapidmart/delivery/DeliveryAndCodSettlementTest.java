package com.rapidmart.delivery;

import com.rapidmart.cart.entity.Cart;
import com.rapidmart.cart.entity.CartItem;
import com.rapidmart.cart.repository.CartItemRepository;
import com.rapidmart.cart.repository.CartRepository;
import com.rapidmart.common.enums.DeliveryStatus;
import com.rapidmart.common.enums.PaymentMethod;
import com.rapidmart.common.enums.PaymentStatus;
import com.rapidmart.common.enums.Role;
import com.rapidmart.common.exception.BusinessException;
import com.rapidmart.customer.entity.CustomerAddress;
import com.rapidmart.customer.repository.CustomerAddressRepository;
import com.rapidmart.delivery.entity.CodSettlement;
import com.rapidmart.delivery.entity.Delivery;
import com.rapidmart.delivery.repository.CodSettlementRepository;
import com.rapidmart.delivery.repository.DeliveryRepository;
import com.rapidmart.delivery.service.CodSettlementService;
import com.rapidmart.delivery.service.DeliveryService;
import com.rapidmart.inventory.entity.Inventory;
import com.rapidmart.inventory.repository.InventoryRepository;
import com.rapidmart.order.dto.CheckoutRequest;
import com.rapidmart.order.dto.OrderDto;
import com.rapidmart.order.service.OrderService;
import com.rapidmart.product.entity.Category;
import com.rapidmart.product.entity.Product;
import com.rapidmart.product.repository.CategoryRepository;
import com.rapidmart.product.repository.ProductRepository;
import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeliveryAndCodSettlementTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private CodSettlementService codSettlementService;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private CodSettlementRepository codSettlementRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerAddressRepository addressRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    private User customer;
    private User driver;
    private User cashier;
    private CustomerAddress address;
    private Product product;
    private Product product2;

    @Autowired
    private com.rapidmart.delivery.repository.DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private com.rapidmart.delivery.repository.DeliveryStatusHistoryRepository historyRepository;

    @Autowired
    private com.rapidmart.delivery.service.DeliveryTrackingService deliveryTrackingService;

    @Autowired
    private com.rapidmart.order.repository.OrderItemRepository orderItemRepository;

    @Autowired
    private com.rapidmart.order.repository.OrderRepository orderRepository;

    @Autowired
    private com.rapidmart.inventory.repository.StockMovementHistoryRepository stockMovementHistoryRepository;

    @Autowired
    private com.rapidmart.payment.repository.PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        codSettlementRepository.deleteAll();
        historyRepository.deleteAll();
        deliveryAttemptRepository.deleteAll();
        deliveryRepository.deleteAll();
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        stockMovementHistoryRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        addressRepository.deleteAll();
        userRepository.deleteAll();

        customer = userRepository.save(new User("custdeliv@rapidmart.local", "pass", "Customer", "+94771", true, Set.of(Role.CUSTOMER)));
        driver = userRepository.save(new User("driver1@rapidmart.local", "pass", "Driver 1", "+94772", true, Set.of(Role.DELIVERY_STAFF)));
        cashier = userRepository.save(new User("cashierdeliv@rapidmart.local", "pass", "Cashier", "+94773", true, Set.of(Role.CASHIER)));

        address = addressRepository.save(new CustomerAddress(customer, "Delivery St 5", "Gampaha", "11000", "+94771", true));

        Category cat = categoryRepository.save(new Category("Delivery Test Beverages", "Drinks", true));
        product = productRepository.save(new Product("SKU-JUICE-01", "Orange Juice", "1L", new BigDecimal("600.00"), "1 L", null, cat, true));
        product2 = productRepository.save(new Product("SKU-JUICE-02", "Apple Juice", "1L", new BigDecimal("500.00"), "1 L", null, cat, true));

        inventoryRepository.save(new Inventory(product, 100, 10, 500));
        inventoryRepository.save(new Inventory(product2, 100, 10, 500));
    }

    @Test
    void testDeliveryAssignmentSlotConflictAndCodCompletion() {
        // Authenticate Customer
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(customer.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );

        Cart cart = cartRepository.findByUserId(customer.getId()).orElseGet(() -> cartRepository.save(new Cart(customer)));
        CartItem item1 = cartItemRepository.save(new CartItem(cart, product, 2));
        cart.getItems().add(item1);
        cartRepository.save(cart);

        LocalDate delivDate = LocalDate.now().plusDays(1);
        CheckoutRequest req = new CheckoutRequest();
        req.setAddressId(address.getId());
        req.setDeliveryDate(delivDate);
        req.setDeliverySlot("10:00-12:00");
        req.setPaymentMethod(PaymentMethod.COD);

        OrderDto orderDto = orderService.checkout(req);

        Delivery delivery = deliveryRepository.findByOrderId(orderDto.getId()).orElseThrow();

        // Configure Driver Slot Limit (Max 1 order for 10:00-12:00)
        deliveryService.setDriverSlotLimit(driver.getId(), delivDate, "10:00-12:00", 1);

        // Assign Driver 1
        Delivery assigned = deliveryService.assignDeliveryStaff(delivery.getId(), driver.getId(), delivDate, "10:00-12:00");
        assertEquals(DeliveryStatus.ASSIGNED, assigned.getDeliveryStatus());
        assertFalse(deliveryService.getDeliveryStaff(null, true, delivDate, "10:00-12:00", null).stream()
                .filter(staff -> staff.id().equals(driver.getId()))
                .findFirst()
                .orElseThrow()
                .available());
        assertThrows(BusinessException.class, () -> deliveryService.assertNoActiveAssignments(driver.getId()));

        // Slot Conflict Test: Assigning Driver 1 to another order on same date/slot must throw SLOT_CONFLICT
        Cart cart2 = cartRepository.findByUserId(customer.getId()).orElseGet(() -> cartRepository.save(new Cart(customer)));
        CartItem item2 = cartItemRepository.save(new CartItem(cart2, product2, 1));
        cart2.getItems().add(item2);
        cartRepository.save(cart2);

        OrderDto orderDto2 = orderService.checkout(req);
        Delivery delivery2 = deliveryRepository.findByOrderId(orderDto2.getId()).orElseThrow();

        assertThrows(BusinessException.class, () ->
                deliveryService.assignDeliveryStaff(delivery2.getId(), driver.getId(), delivDate, "10:00-12:00")
        );

        // Driver marks Out For Delivery & Complete
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(driver.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_DELIVERY_STAFF")))
        );

        deliveryService.markOutForDelivery(assigned.getId());

        // COD requires the exact order total to be collected before completion
        assertThrows(BusinessException.class, () ->
                deliveryService.completeDelivery(assigned.getId(), new BigDecimal("1000.00"))
        );
        Delivery completed = deliveryService.completeDelivery(assigned.getId(), new BigDecimal("1200.00"));

        assertEquals(DeliveryStatus.DELIVERED_ON_TIME, completed.getDeliveryStatus());

        // Completing again is idempotent and must not duplicate the settlement
        deliveryService.completeDelivery(assigned.getId(), new BigDecimal("1200.00"));
        assertEquals(1, codSettlementRepository.findAll().size());

        // Check COD Settlement creation
        CodSettlement settlement = codSettlementRepository.findByDeliveryId(assigned.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1200.00").compareTo(settlement.getCollectedAmount()));

        // The driver completing delivery records the collected cash but does NOT verify the payment:
        // it stays PENDING until a cashier confirms cash receipt.
        var paymentAfterDelivery = paymentRepository.findByOrderId(orderDto.getId()).orElseThrow();
        assertEquals(PaymentStatus.PENDING, paymentAfterDelivery.getPaymentStatus());
        assertNull(paymentAfterDelivery.getVerifiedBy());
        assertNull(paymentAfterDelivery.getVerifiedAt());

        // Cashier confirms cash receipt: this both settles the cash and verifies the payment (Pending -> Paid)
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(cashier.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_CASHIER")))
        );

        CodSettlement confirmed = codSettlementService.confirmCashReceipt(settlement.getId());
        assertTrue(confirmed.isConfirmedByCashier());

        var paymentAfterConfirm = paymentRepository.findByOrderId(orderDto.getId()).orElseThrow();
        assertEquals(PaymentStatus.PAID, paymentAfterConfirm.getPaymentStatus());
        assertEquals(cashier.getId(), paymentAfterConfirm.getVerifiedBy().getId());
        assertNotNull(paymentAfterConfirm.getVerifiedAt());

        // Confirming again is idempotent: the verification fields are left untouched
        var verifiedAtBefore = paymentAfterConfirm.getVerifiedAt();
        codSettlementService.confirmCashReceipt(settlement.getId());
        var paymentAfterSecondConfirm = paymentRepository.findByOrderId(orderDto.getId()).orElseThrow();
        assertEquals(verifiedAtBefore, paymentAfterSecondConfirm.getVerifiedAt());
    }

    @Test
    void testDeliveryTrackingHistoryAndTransitionRules() {
        // 1. Authenticate Customer & Checkout
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(customer.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );

        Cart cart = cartRepository.findByUserId(customer.getId()).orElseGet(() -> cartRepository.save(new Cart(customer)));
        CartItem item1 = cartItemRepository.save(new CartItem(cart, product, 1));
        cart.getItems().add(item1);
        cartRepository.save(cart);

        LocalDate delivDate = LocalDate.now().plusDays(2);
        CheckoutRequest req = new CheckoutRequest();
        req.setAddressId(address.getId());
        req.setDeliveryDate(delivDate);
        req.setDeliverySlot("14:00-16:00");
        req.setPaymentMethod(PaymentMethod.COD);

        OrderDto orderDto = orderService.checkout(req);
        Delivery delivery = deliveryRepository.findByOrderId(orderDto.getId()).orElseThrow();

        // Check initial tracking history: a new delivery stays PENDING (the coordinator's queue status)
        // and its first tracking event is the order placement.
        var trackingInitial = deliveryTrackingService.getCustomerOrderTracking(orderDto.getId());
        assertEquals(DeliveryStatus.PENDING, trackingInitial.getCurrentStatus());
        assertEquals(DeliveryStatus.PENDING, deliveryRepository.findById(delivery.getId()).orElseThrow().getDeliveryStatus());
        assertFalse(trackingInitial.getHistory().isEmpty());
        assertEquals(DeliveryStatus.PENDING, trackingInitial.getHistory().get(0).status());

        // 2. Staff status updates: PENDING -> PROCESSING -> READY_FOR_DELIVERY -> OUT_FOR_DELIVERY -> DELIVERED
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(driver.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_DELIVERY_STAFF")))
        );

        // Assign driver with configured limit
        deliveryService.setDriverSlotLimit(driver.getId(), delivDate, "14:00-16:00", 5);
        deliveryService.assignDeliveryStaff(delivery.getId(), driver.getId(), delivDate, "14:00-16:00");

        // Staff updates status to PROCESSING via PATCH endpoint service method
        var trackingProc = deliveryTrackingService.updateDeliveryStatusByStaff(delivery.getId(), DeliveryStatus.PROCESSING, "Items picked and packed");
        assertEquals(DeliveryStatus.PROCESSING, trackingProc.getCurrentStatus());

        // Staff updates to OUT_FOR_DELIVERY
        var trackingOut = deliveryTrackingService.updateDeliveryStatusByStaff(delivery.getId(), DeliveryStatus.OUT_FOR_DELIVERY, "Package left hub");
        assertEquals(DeliveryStatus.OUT_FOR_DELIVERY, trackingOut.getCurrentStatus());

        // Staff updates to DELIVERED
        var trackingDelivered = deliveryTrackingService.updateDeliveryStatusByStaff(delivery.getId(), DeliveryStatus.DELIVERED, "Handed to customer");
        assertEquals(DeliveryStatus.DELIVERED, trackingDelivered.getCurrentStatus());
        assertNotNull(trackingDelivered.getCompletedAt());

        // 3. Test Invalid Transition Rule: DELIVERED -> PROCESSING must be rejected
        assertThrows(BusinessException.class, () ->
                deliveryTrackingService.updateDeliveryStatusByStaff(delivery.getId(), DeliveryStatus.PROCESSING, "Try invalid transition")
        );

        // Verify total history entries recorded
        var historyRecords = historyRepository.findByDeliveryIdOrderByCreatedAtAscIdAsc(delivery.getId());
        assertTrue(historyRecords.size() >= 4, "Expected history entries for ORDER_CONFIRMED, ASSIGNED, PROCESSING, OUT_FOR_DELIVERY, DELIVERED");
    }
}
