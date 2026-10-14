package com.rapidmart.order;

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
import com.rapidmart.order.dto.OrderDto;
import com.rapidmart.order.repository.OrderRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies tax and delivery charge (both zero by default elsewhere) are computed from config and folded into the total. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "rapidmart.tax.rate-percentage=10",
        "rapidmart.delivery.charge=150.00"
})
@Transactional
class OrderPricingTaxDeliveryTest {

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

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private com.rapidmart.payment.repository.PaymentRepository paymentRepository;

    @Autowired
    private com.rapidmart.delivery.repository.DeliveryRepository deliveryRepository;

    @Autowired
    private com.rapidmart.order.repository.OrderItemRepository orderItemRepository;

    @Autowired
    private com.rapidmart.inventory.repository.StockMovementHistoryRepository stockMovementHistoryRepository;

    private User customer;
    private CustomerAddress address;
    private Product product;

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

        customer = userRepository.save(new User("taxdelivery@rapidmart.local", "pass", "Tax Customer", "+94770009999", true, Set.of(Role.CUSTOMER)));
        address = addressRepository.save(new CustomerAddress(customer, "1 Tax Street", "Colombo", "10000", "+94770009999", true));

        Category category = categoryRepository.save(new Category("Pricing Test", "Tax/delivery pricing", true));
        product = productRepository.save(new Product("SKU-TAX-01", "Priced Item", "unit", new BigDecimal("1000.00"), "unit", null, category, true));
        inventoryRepository.save(new Inventory(product, 50, 5, 200));
    }

    @Test
    void checkoutFoldsConfiguredTaxAndDeliveryChargeIntoTotal() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(customer.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );

        Cart cart = cartRepository.findByUserId(customer.getId()).orElseGet(() -> cartRepository.save(new Cart(customer)));
        CartItem item = cartItemRepository.save(new CartItem(cart, product, 1));
        cart.getItems().add(item);
        cartRepository.save(cart);

        CheckoutRequest req = new CheckoutRequest();
        req.setAddressId(address.getId());
        req.setDeliveryDate(LocalDate.now().plusDays(2));
        req.setDeliverySlot("09:00-11:00");
        req.setPaymentMethod(PaymentMethod.COD);

        OrderDto order = orderService.checkout(req);

        // Subtotal 1000.00, 10% tax on the discounted merchandise value = 100.00, flat delivery charge = 150.00
        assertEquals(0, new BigDecimal("1000.00").compareTo(order.getSubtotal()));
        assertEquals(0, new BigDecimal("100.00").compareTo(order.getTaxAmount()));
        assertEquals(0, new BigDecimal("150.00").compareTo(order.getDeliveryCharge()));
        assertEquals(0, new BigDecimal("1250.00").compareTo(order.getTotalAmount()));
    }
}
