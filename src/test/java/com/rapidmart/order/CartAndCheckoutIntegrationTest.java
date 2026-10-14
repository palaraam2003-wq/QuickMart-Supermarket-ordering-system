package com.rapidmart.order;

import com.rapidmart.cart.entity.Cart;
import com.rapidmart.cart.entity.CartItem;
import com.rapidmart.cart.repository.CartItemRepository;
import com.rapidmart.cart.repository.CartRepository;
import com.rapidmart.common.enums.Role;
import com.rapidmart.customer.entity.CustomerAddress;
import com.rapidmart.customer.repository.CustomerAddressRepository;
import com.rapidmart.inventory.entity.Inventory;
import com.rapidmart.inventory.repository.InventoryRepository;
import com.rapidmart.order.repository.OrderRepository;
import com.rapidmart.product.entity.Category;
import com.rapidmart.product.entity.Product;
import com.rapidmart.product.repository.CategoryRepository;
import com.rapidmart.product.repository.ProductRepository;
import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CartAndCheckoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
    private PasswordEncoder passwordEncoder;

    private User customer;
    private CustomerAddress address;
    private Product product;
    private Inventory inventory;

    @Autowired
    private com.rapidmart.payment.repository.PaymentRepository paymentRepository;

    @Autowired
    private com.rapidmart.delivery.repository.DeliveryRepository deliveryRepository;

    @Autowired
    private com.rapidmart.order.repository.OrderItemRepository orderItemRepository;

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

        customer = new User("checkoutuser@rapidmart.local", passwordEncoder.encode("Pass123!"), "Checkout Customer", "+94770001111", true, Set.of(Role.CUSTOMER));
        customer = userRepository.save(customer);

        address = addressRepository.save(new CustomerAddress(customer, "789 High Street", "Kandy", "20000", "+94770001111", true));

        Category category = categoryRepository.save(new Category("Dairy", "Fresh milk & cheese", true));
        product = productRepository.save(new Product("SKU-MILK-01", "Fresh Whole Milk", "1L bottle", new BigDecimal("450.00"), "1 L", null, category, true));

        inventory = new Inventory(product, 50, 5, 200);
        inventoryRepository.save(inventory);
    }

    @Test
    @WithMockUser(username = "checkoutuser@rapidmart.local", roles = "CUSTOMER")
    void testCheckoutSuccessfulFlowAndStockDeduction() throws Exception {
        Cart cart = cartRepository.findByUserId(customer.getId()).orElseGet(() -> cartRepository.save(new Cart(customer)));
        CartItem item = cartItemRepository.save(new CartItem(cart, product, 2));
        cart.getItems().add(item);
        cartRepository.save(cart);

        String json = String.format("""
            {
                "addressId": %d,
                "deliveryDate": "%s",
                "deliverySlot": "09:00-11:00",
                "paymentMethod": "COD"
            }
            """, address.getId(), LocalDate.now().plusDays(2));

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(900.00))
                .andExpect(jsonPath("$.taxAmount").value(0.00))
                .andExpect(jsonPath("$.deliveryCharge").value(0.00))
                .andExpect(jsonPath("$.totalAmount").value(900.00));

        // Verify stock deducted from 50 to 48
        Inventory updatedInv = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        assertEquals(48, updatedInv.getQuantity());

        // Verify cart is cleared
        Cart clearedCart = cartRepository.findByUserId(customer.getId()).orElseThrow();
        assertTrue(clearedCart.getItems().isEmpty());
    }

    @Test
    @WithMockUser(username = "checkoutuser@rapidmart.local", roles = "CUSTOMER")
    void testCheckoutRejectsPastOrTodayDeliveryDate() throws Exception {
        Cart cart = cartRepository.findByUserId(customer.getId()).orElseGet(() -> cartRepository.save(new Cart(customer)));
        CartItem item = cartItemRepository.save(new CartItem(cart, product, 1));
        cart.getItems().add(item);
        cartRepository.save(cart);

        String json = String.format("""
            {
                "addressId": %d,
                "deliveryDate": "%s",
                "deliverySlot": "09:00-11:00",
                "paymentMethod": "COD"
            }
            """, address.getId(), LocalDate.now().minusDays(1)); // Past date triggers @Future validation!

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }
}
