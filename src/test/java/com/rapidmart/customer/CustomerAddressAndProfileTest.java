package com.rapidmart.customer;

import com.rapidmart.common.enums.Role;
import com.rapidmart.customer.entity.CustomerAddress;
import com.rapidmart.customer.repository.CustomerAddressRepository;
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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerAddressAndProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerAddressRepository addressRepository;

    @Autowired
    private com.rapidmart.billing.repository.BillRepository billRepository;

    @Autowired
    private com.rapidmart.delivery.repository.DeliveryRepository deliveryRepository;

    @Autowired
    private com.rapidmart.delivery.repository.CodSettlementRepository codSettlementRepository;

    @Autowired
    private com.rapidmart.payment.repository.PaymentRepository paymentRepository;

    @Autowired
    private com.rapidmart.refund.repository.RefundRequestRepository refundRequestRepository;

    @Autowired
    private com.rapidmart.order.repository.OrderItemRepository orderItemRepository;

    @Autowired
    private com.rapidmart.order.repository.OrderRepository orderRepository;

    @Autowired
    private com.rapidmart.cart.repository.CartRepository cartRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        billRepository.deleteAll();
        codSettlementRepository.deleteAll();
        deliveryRepository.deleteAll();
        refundRequestRepository.deleteAll();
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        addressRepository.deleteAll();
        userRepository.deleteAll();

        user1 = new User("customer1@rapidmart.local", passwordEncoder.encode("Pass123!"), "Customer One", "+94771111111", true, Set.of(Role.CUSTOMER));
        user2 = new User("customer2@rapidmart.local", passwordEncoder.encode("Pass123!"), "Customer Two", "+94772222222", true, Set.of(Role.CUSTOMER));
        user1 = userRepository.save(user1);
        user2 = userRepository.save(user2);
    }

    @Test
    @WithMockUser(username = "customer1@rapidmart.local", roles = "CUSTOMER")
    void testCreateAndGetMyAddresses() throws Exception {
        String json = """
            {
                "streetAddress": "123 Main Street",
                "city": "Colombo",
                "postalCode": "00100",
                "phoneContact": "+94771111111",
                "isDefault": true
            }
            """;

        mockMvc.perform(post("/api/v1/customer/addresses")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.streetAddress").value("123 Main Street"))
                .andExpect(jsonPath("$.city").value("Colombo"));

        mockMvc.perform(get("/api/v1/customer/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(username = "customer2@rapidmart.local", roles = "CUSTOMER")
    void testCannotAccessOrModifyOtherCustomerAddress() throws Exception {
        CustomerAddress addr1 = new CustomerAddress(user1, "456 Ocean Road", "Galle", "80000", "+94771111111", true);
        addr1 = addressRepository.save(addr1);

        // Attempting to delete user1's address as user2
        mockMvc.perform(delete("/api/v1/customer/addresses/" + addr1.getId())
                        .with(csrf()))
                .andExpect(status().isNotFound());

        // Verify address remains untouched
        assertTrue(addressRepository.findById(addr1.getId()).isPresent());
    }
}
