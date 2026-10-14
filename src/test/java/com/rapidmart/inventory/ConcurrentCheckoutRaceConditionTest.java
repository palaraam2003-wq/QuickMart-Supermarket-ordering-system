package com.rapidmart.inventory;

import com.rapidmart.cart.entity.Cart;
import com.rapidmart.cart.entity.CartItem;
import com.rapidmart.cart.repository.CartItemRepository;
import com.rapidmart.cart.repository.CartRepository;
import com.rapidmart.common.enums.Role;
import com.rapidmart.customer.entity.CustomerAddress;
import com.rapidmart.customer.repository.CustomerAddressRepository;
import com.rapidmart.inventory.entity.Inventory;
import com.rapidmart.inventory.repository.InventoryRepository;
import com.rapidmart.order.dto.CheckoutRequest;
import com.rapidmart.order.service.OrderService;
import com.rapidmart.common.enums.PaymentMethod;
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
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentCheckoutRaceConditionTest {

    @Autowired
    private OrderService orderService;

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

    private User userA;
    private User userB;
    private CustomerAddress addressA;
    private CustomerAddress addressB;
    private Product rareProduct;

    @Autowired
    private com.rapidmart.delivery.repository.DeliveryRepository deliveryRepository;

    @Autowired
    private com.rapidmart.payment.repository.PaymentRepository paymentRepository;

    @Autowired
    private com.rapidmart.order.repository.OrderItemRepository orderItemRepository;

    @Autowired
    private com.rapidmart.order.repository.OrderRepository orderRepository;

    @Autowired
    private com.rapidmart.inventory.repository.StockMovementHistoryRepository stockMovementHistoryRepository;

    @BeforeEach
    void setUp() {
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

        userA = userRepository.save(new User("userA@rapidmart.local", "pass", "User A", "+94770001", true, Set.of(Role.CUSTOMER)));
        userB = userRepository.save(new User("userB@rapidmart.local", "pass", "User B", "+94770002", true, Set.of(Role.CUSTOMER)));

        addressA = addressRepository.save(new CustomerAddress(userA, "Street A", "City A", "1000", "+94770001", true));
        addressB = addressRepository.save(new CustomerAddress(userB, "Street B", "City B", "2000", "+94770002", true));

        Category cat = categoryRepository.save(new Category("Limited Items", "Rare items", true));
        rareProduct = productRepository.save(new Product("SKU-RARE-01", "Limited Edition GPU", "1 unit left", new BigDecimal("150000.00"), "1 pc", null, cat, true));

        // EXACTLY 1 UNIT IN STOCK
        inventoryRepository.save(new Inventory(rareProduct, 1, 0, 10));

        // User A cart has 1 unit
        Cart cartA = cartRepository.save(new Cart(userA));
        cartItemRepository.save(new CartItem(cartA, rareProduct, 1));

        // User B cart has 1 unit
        Cart cartB = cartRepository.save(new Cart(userB));
        cartItemRepository.save(new CartItem(cartB, rareProduct, 1));
    }

    @Test
    void testConcurrentCheckoutLastUnitOnlyOneSucceeds() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Callable<Void> taskA = () -> {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userA.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
            );
            barrier.await();
            try {
                CheckoutRequest req = new CheckoutRequest();
                req.setAddressId(addressA.getId());
                req.setDeliveryDate(LocalDate.now().plusDays(1));
                req.setDeliverySlot("09:00-11:00");
                req.setPaymentMethod(PaymentMethod.COD);
                orderService.checkout(req);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                SecurityContextHolder.clearContext();
            }
            return null;
        };

        Callable<Void> taskB = () -> {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userB.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
            );
            barrier.await();
            try {
                CheckoutRequest req = new CheckoutRequest();
                req.setAddressId(addressB.getId());
                req.setDeliveryDate(LocalDate.now().plusDays(1));
                req.setDeliverySlot("09:00-11:00");
                req.setPaymentMethod(PaymentMethod.COD);
                orderService.checkout(req);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                SecurityContextHolder.clearContext();
            }
            return null;
        };

        Future<Void> f1 = executor.submit(taskA);
        Future<Void> f2 = executor.submit(taskB);

        f1.get();
        f2.get();
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one customer should succeed");
        assertEquals(1, failureCount.get(), "Exactly one customer should fail due to stock depletion");

        Inventory finalInv = inventoryRepository.findByProductId(rareProduct.getId()).orElseThrow();
        assertEquals(0, finalInv.getQuantity(), "Final stock should be 0, never negative");
    }
}
