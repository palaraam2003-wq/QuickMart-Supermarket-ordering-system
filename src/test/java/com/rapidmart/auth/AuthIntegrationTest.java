package com.rapidmart.auth;

import com.rapidmart.common.enums.Role;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

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
    private com.rapidmart.customer.repository.CustomerAddressRepository addressRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
    }

    @Test
    void testPublicRegistrationCreatesOnlyCustomerRole() throws Exception {
        String json = """
            {
                "email": "testcustomer@rapidmart.com",
                "password": "Password123!",
                "fullName": "Test Customer",
                "phone": "+94771234567"
            }
            """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("testcustomer@rapidmart.com"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));

        User user = userRepository.findByEmailIgnoreCase("testcustomer@rapidmart.com").orElseThrow();
        assertEquals(Set.of(Role.CUSTOMER), user.getRoles());
    }

    @Test
    void testDuplicateEmailRegistrationFails() throws Exception {
        User user = new User("duplicate@rapidmart.com", passwordEncoder.encode("Pass123!"), "User One", null, true, Set.of(Role.CUSTOMER));
        userRepository.save(user);

        String json = """
            {
                "email": "duplicate@rapidmart.com",
                "password": "Password123!",
                "fullName": "User Two",
                "phone": "+94771234567"
            }
            """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @WithMockUser(username = "disabled@rapidmart.local", roles = "CUSTOMER")
    void testDeactivatedAccountAccessIsDenied() throws Exception {
        User user = new User("disabled@rapidmart.local", passwordEncoder.encode("Pass123!"), "Disabled User", null, false, Set.of(Role.CUSTOMER));
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_DISABLED"));
    }

    @Test
    @WithMockUser(username = "customer@rapidmart.local", roles = "CUSTOMER")
    void testCustomerForbiddenFromManagerEndpoints() throws Exception {
        User user = new User("customer@rapidmart.local", passwordEncoder.encode("Pass123!"), "Customer User", null, true, Set.of(Role.CUSTOMER));
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/manager/employees"))
                .andExpect(status().isForbidden());
    }
}
