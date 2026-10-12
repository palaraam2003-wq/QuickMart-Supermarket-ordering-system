package com.rapidmart.delivery;

import com.rapidmart.cart.entity.Cart;
import com.rapidmart.cart.entity.CartItem;
import com.rapidmart.cart.repository.CartItemRepository;
import com.rapidmart.cart.repository.CartRepository;
import com.rapidmart.common.enums.DeliveryStatus;
import com.rapidmart.common.enums.PaymentMethod;
import com.rapidmart.common.enums.Role;
import com.rapidmart.common.exception.BusinessException;
import com.rapidmart.customer.entity.CustomerAddress;
import com.rapidmart.customer.repository.CustomerAddressRepository;
import com.rapidmart.delivery.dto.DelayedDeliveryDto;
import com.rapidmart.delivery.dto.DriverSlotLimitDto;
import com.rapidmart.delivery.entity.Delivery;
import com.rapidmart.delivery.repository.CodSettlementRepository;
import com.rapidmart.delivery.repository.DeliveryAttemptRepository;
import com.rapidmart.delivery.repository.DeliveryRepository;
import com.rapidmart.delivery.repository.DeliveryStatusHistoryRepository;
import com.rapidmart.delivery.repository.DriverSlotLimitRepository;
import com.rapidmart.delivery.service.DeliveryService;
import com.rapidmart.inventory.entity.Inventory;
import com.rapidmart.inventory.repository.InventoryRepository;
import com.rapidmart.inventory.repository.StockMovementHistoryRepository;
import com.rapidmart.order.dto.CheckoutRequest;
import com.rapidmart.order.dto.OrderDto;
import com.rapidmart.order.repository.OrderItemRepository;
import com.rapidmart.order.repository.OrderRepository;
import com.rapidmart.order.service.OrderService;
import com.rapidmart.payment.repository.PaymentRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DriverOrderLimitsAndOverdueTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DriverSlotLimitRepository driverSlotLimitRepository;

    @Autowired
    private CodSettlementRepository codSettlementRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private DeliveryStatusHistoryRepository historyRepository;

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

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StockMovementHistoryRepository stockMovementHistoryRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private User coordinator;
    private User customer;
    private User driver;
    private CustomerAddress address;
    private Product product;

    @BeforeEach
    void setUp() {
        codSettlementRepository.deleteAll();
        historyRepository.deleteAll();
        deliveryAttemptRepository.deleteAll();
        deliveryRepository.deleteAll();
        driverSlotLimitRepository.deleteAll();
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

        coordinator = userRepository.save(new User("coord@rapidmart.local", "pass", "Coordinator", "+9477000", true, Set.of(Role.DELIVERY_COORDINATOR)));
        customer = userRepository.save(new User("custlimits@rapidmart.local", "pass", "Customer Limits", "+9477001", true, Set.of(Role.CUSTOMER)));
        driver = userRepository.save(new User("driverlimits@rapidmart.local", "pass", "Driver Limits", "+9477002", true, Set.of(Role.DELIVERY_STAFF)));

        address = addressRepository.save(new CustomerAddress(customer, "Limit St 12", "Colombo", "00100", "+9477001", true));

        Category cat = categoryRepository.save(new Category("Limit Snacks", "Snacks", true));
        product = productRepository.save(new Product("SKU-CHIPS-01", "Potato Chips", "100g", new BigDecimal("350.00"), "1 Pack", null, cat, true));

        inventoryRepository.save(new Inventory(product, 200, 10, 1000));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(coordinator.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_DELIVERY_COORDINATOR")))
        );
    }

    private int orderSeq = 1;

    private Delivery createCustomerOrder(LocalDate date, String slot) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(customer.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );

        Category cat = categoryRepository.findAll().get(0);
        Product p = productRepository.save(new Product("SKU-PROD-" + (orderSeq++), "Test Product " + orderSeq, "100g", new BigDecimal("100.00"), "1 Pack", null, cat, true));
        inventoryRepository.save(new Inventory(p, 100, 10, 500));

        Cart cart = cartRepository.findByUserId(customer.getId()).orElseGet(() -> cartRepository.save(new Cart(customer)));
        CartItem item = cartItemRepository.save(new CartItem(cart, p, 1));
        cart.getItems().add(item);
        cartRepository.save(cart);

        CheckoutRequest req = new CheckoutRequest();
        req.setAddressId(address.getId());
        req.setDeliveryDate(date);
        req.setDeliverySlot(slot);
        req.setPaymentMethod(PaymentMethod.COD);

        OrderDto orderDto = orderService.checkout(req);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(coordinator.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_DELIVERY_COORDINATOR")))
        );

        return deliveryRepository.findByOrderId(orderDto.getId()).orElseThrow();
    }

    @Test
    void testSetAndUpdateDriverSlotLimitAndCapacity() {
        LocalDate date = LocalDate.now().plusDays(1);
        String slot = "MORNING_09_12";

        // Set initial limit = 3
        DriverSlotLimitDto dto = deliveryService.setDriverSlotLimit(driver.getId(), date, slot, 3);
        assertEquals(3, dto.maxOrders());
        assertEquals(0, dto.assignedCount());
        assertEquals(3, dto.remainingCapacity());

        // Update limit = 5
        DriverSlotLimitDto updatedDto = deliveryService.setDriverSlotLimit(driver.getId(), date, slot, 5);
        assertEquals(5, updatedDto.maxOrders());
        assertEquals(0, updatedDto.assignedCount());
        assertEquals(5, updatedDto.remainingCapacity());

        // Retrieve limit via query
        List<DriverSlotLimitDto> list = deliveryService.getDriverSlotLimits(date, slot, driver.getId());
        assertEquals(1, list.size());
        assertEquals(5, list.get(0).maxOrders());
    }

    @Test
    void testMissingLimitValidation() {
        LocalDate date = LocalDate.now().plusDays(1);
        String slot = "AFTERNOON_12_15";

        Delivery delivery = createCustomerOrder(date, slot);

        // Attempting to assign driver without setting limit throws MISSING_LIMIT
        BusinessException ex = assertThrows(BusinessException.class, () ->
                deliveryService.assignDeliveryStaff(delivery.getId(), driver.getId(), date, slot)
        );
        assertEquals("MISSING_LIMIT", ex.getErrorCode());
    }

    @Test
    void testLimitExceededPrevention() {
        LocalDate date = LocalDate.now().plusDays(1);
        String slot = "MORNING_09_12";

        // Set limit = 2
        deliveryService.setDriverSlotLimit(driver.getId(), date, slot, 2);

        Delivery delivery1 = createCustomerOrder(date, slot);
        Delivery delivery2 = createCustomerOrder(date, slot);
        Delivery delivery3 = createCustomerOrder(date, slot);

        // Assign 1st order -> succeeds (1/2 assigned)
        deliveryService.assignDeliveryStaff(delivery1.getId(), driver.getId(), date, slot);

        // Assign 2nd order -> succeeds (2/2 assigned)
        deliveryService.assignDeliveryStaff(delivery2.getId(), driver.getId(), date, slot);

        // Check remaining capacity = 0
        DriverSlotLimitDto limitDto = deliveryService.getDriverSlotLimits(date, slot, driver.getId()).get(0);
        assertEquals(2, limitDto.assignedCount());
        assertEquals(0, limitDto.remainingCapacity());

        // Assign 3rd order -> throws LIMIT_EXCEEDED
        BusinessException ex = assertThrows(BusinessException.class, () ->
                deliveryService.assignDeliveryStaff(delivery3.getId(), driver.getId(), date, slot)
        );
        assertEquals("LIMIT_EXCEEDED", ex.getErrorCode());
    }

    @Test
    void testDuplicateAssignmentValidation() {
        LocalDate date = LocalDate.now().plusDays(1);
        String slot = "EVENING_15_18";

        deliveryService.setDriverSlotLimit(driver.getId(), date, slot, 3);
        Delivery delivery = createCustomerOrder(date, slot);

        deliveryService.assignDeliveryStaff(delivery.getId(), driver.getId(), date, slot);

        // Assigning the same order again to the same driver/slot throws DUPLICATE_ASSIGNMENT
        BusinessException ex = assertThrows(BusinessException.class, () ->
                deliveryService.assignDeliveryStaff(delivery.getId(), driver.getId(), date, slot)
        );
        assertEquals("DUPLICATE_ASSIGNMENT", ex.getErrorCode());
    }

    @Test
    void testOverdueDetectionReschedulingAndDelayedDeliveries() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        String slot = "MORNING_09_12";

        deliveryService.setDriverSlotLimit(driver.getId(), tomorrow, slot, 5);
        Delivery delivery = createCustomerOrder(tomorrow, slot);

        // Assign driver
        deliveryService.assignDeliveryStaff(delivery.getId(), driver.getId(), tomorrow, slot);

        // Simulate slot passing by setting scheduledDate to yesterday (limits cannot be set for past dates)
        delivery.setScheduledDate(yesterday);
        deliveryRepository.save(delivery);

        // Check overdue evaluation
        deliveryService.checkAndUpdateOverdueDeliveries();

        Delivery overdueDelivery = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertEquals(DeliveryStatus.OVERDUE, overdueDelivery.getDeliveryStatus());

        // Reschedule overdue delivery to tomorrow
        String newSlot = "EVENING_15_18";

        deliveryService.setDriverSlotLimit(driver.getId(), tomorrow, newSlot, 5);
        Delivery rescheduled = deliveryService.assignDeliveryStaff(overdueDelivery.getId(), driver.getId(), tomorrow, newSlot);

        assertEquals(DeliveryStatus.RESCHEDULED, rescheduled.getDeliveryStatus());
        assertEquals(tomorrow, rescheduled.getScheduledDate());

        // Driver completes delivery
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(driver.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_DELIVERY_STAFF")))
        );

        deliveryService.markOutForDelivery(rescheduled.getId());
        Delivery completed = deliveryService.completeDelivery(rescheduled.getId(), rescheduled.getOrder().getTotalAmount());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(coordinator.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_DELIVERY_COORDINATOR")))
        );

        assertEquals(DeliveryStatus.DELIVERED_ON_TIME, completed.getDeliveryStatus());

        // Verify delayed deliveries audit report includes history of overdue delivery
        List<DelayedDeliveryDto> delayedList = deliveryService.getDelayedDeliveries();
        assertFalse(delayedList.isEmpty());
        DelayedDeliveryDto delayed = delayedList.get(0);
        assertEquals(delivery.getId(), delayed.deliveryId());
        assertEquals(driver.getId(), delayed.assignedStaffId());
        assertNotNull(delayed.actualDeliveryTime());
    }
}
