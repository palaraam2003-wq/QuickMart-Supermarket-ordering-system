package com.rapidmart.product;

import com.rapidmart.cart.repository.CartItemRepository;
import com.rapidmart.common.enums.Role;
import com.rapidmart.inventory.entity.Inventory;
import com.rapidmart.inventory.repository.InventoryRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductCatalogueInventoryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Category category;
    private Product activeProduct;
    private Product inactiveProduct;

    @Autowired
    private com.rapidmart.inventory.repository.StockMovementHistoryRepository stockMovementHistoryRepository;

    @Autowired
    private com.rapidmart.order.repository.OrderItemRepository orderItemRepository;

    @Autowired
    private com.rapidmart.order.repository.OrderRepository orderRepository;

    @Autowired
    private com.rapidmart.delivery.repository.DeliveryRepository deliveryRepository;

    @Autowired
    private com.rapidmart.payment.repository.PaymentRepository paymentRepository;

    @Autowired
    private com.rapidmart.billing.repository.BillRepository billRepository;

    @Autowired
    private com.rapidmart.refund.repository.RefundRequestRepository refundRequestRepository;

    @BeforeEach
    void setUp() {
        refundRequestRepository.deleteAll();
        deliveryRepository.deleteAll();
        billRepository.deleteAll();
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        stockMovementHistoryRepository.deleteAll();
        cartItemRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        User staff = new User("staff@rapidmart.local", passwordEncoder.encode("Pass123!"), "Inventory Staff", null, true, Set.of(Role.INVENTORY_STAFF));
        userRepository.save(staff);

        category = categoryRepository.save(new Category("Fresh Produce", "Fresh fruits and vegetables", true));
        activeProduct = productRepository.save(new Product("SKU-APP-01", "Fresh Apple", "Crisp red apples", new BigDecimal("150.00"), "1 kg", null, category, true));
        inactiveProduct = productRepository.save(new Product("SKU-BAN-01", "Yellow Banana", "Sweet bananas", new BigDecimal("100.00"), "1 kg", null, category, false));

        inventoryRepository.save(new Inventory(activeProduct, 100, 10, 500));
        inventoryRepository.save(new Inventory(inactiveProduct, 50, 10, 500));
    }

    @Test
    void testPublicBrowseProductsReturnsOnlyActive() throws Exception {
        mockMvc.perform(get("/api/v1/products/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Fresh Apple"));
    }

    @Test
    void testSearchByName() throws Exception {
        mockMvc.perform(get("/api/v1/products/public").param("name", "Apple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Fresh Apple"));
    }

    @Test
    @WithMockUser(username = "staff@rapidmart.local", roles = "INVENTORY_STAFF")
    void testInventoryStaffDeactivateProduct() throws Exception {
        mockMvc.perform(delete("/api/v1/inventory/products/" + activeProduct.getId())
                        .with(csrf()))
                .andExpect(status().isNoContent());

        Product fetched = productRepository.findById(activeProduct.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertFalse(fetched.isActive());
    }
}

