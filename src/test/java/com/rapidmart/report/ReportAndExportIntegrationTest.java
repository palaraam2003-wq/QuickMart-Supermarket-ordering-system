package com.rapidmart.report;

import com.rapidmart.cart.entity.Cart;
import com.rapidmart.cart.entity.CartItem;
import com.rapidmart.cart.repository.CartItemRepository;
import com.rapidmart.cart.repository.CartRepository;
import com.rapidmart.common.enums.PaymentMethod;
import com.rapidmart.common.enums.Role;
import com.rapidmart.customer.entity.CustomerAddress;
import com.rapidmart.customer.repository.CustomerAddressRepository;
import com.rapidmart.inventory.entity.Inventory;
import com.rapidmart.inventory.repository.InventoryRepository;
import com.rapidmart.order.dto.CheckoutRequest;
import com.rapidmart.order.service.OrderService;
import com.rapidmart.product.entity.Category;
import com.rapidmart.product.entity.Product;
import com.rapidmart.product.repository.CategoryRepository;
import com.rapidmart.product.repository.ProductRepository;
import com.rapidmart.report.service.ReportService;
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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReportAndExportIntegrationTest {

    @Autowired
    private ReportService reportService;

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

    private User customer;
    private CustomerAddress address;
    private Product product;

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

        customer = userRepository.save(new User("repuser@rapidmart.local", "pass", "=FormulaUser", "+94771", true, Set.of(Role.CUSTOMER)));
        address = addressRepository.save(new CustomerAddress(customer, "Report St", "Colombo", "00100", "+94771", true));

        Category cat = categoryRepository.save(new Category("Snacks", "Biscuits & Chips", true));
        product = productRepository.save(new Product("SKU-CHIP-01", "+Chips", "Salted chips", new BigDecimal("300.00"), "1 pack", null, cat, true));

        inventoryRepository.save(new Inventory(product, 100, 10, 500));
    }

    @Test
    void testSalesSummaryAndCsvFormulaProtection() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(customer.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );

        Cart cart = cartRepository.findByUserId(customer.getId()).orElseGet(() -> cartRepository.save(new Cart(customer)));
        CartItem item = cartItemRepository.save(new CartItem(cart, product, 3));
        cart.getItems().add(item);
        cartRepository.save(cart);

        CheckoutRequest req = new CheckoutRequest();
        req.setAddressId(address.getId());
        req.setDeliveryDate(LocalDate.now().plusDays(2));
        req.setDeliverySlot("14:00-16:00");
        req.setPaymentMethod(PaymentMethod.COD);

        orderService.checkout(req);

        Map<String, Object> summary = reportService.getSalesSummary(null, null);
        assertEquals(1L, summary.get("totalOrders"));
        assertEquals(0, new BigDecimal("900.00").compareTo((BigDecimal) summary.get("totalRevenue")));

        String csv = reportService.generateSalesCsv();
        assertNotNull(csv);
        assertTrue(csv.contains("Order Number,Customer Email,Delivery Date,Status,Total Amount (LKR)"));
        assertFalse(csv.contains("\n=")); // Ensure formula starting with = or + is sanitized!
    }
}
