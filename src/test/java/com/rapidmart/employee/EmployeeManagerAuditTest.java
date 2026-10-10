package com.rapidmart.employee;

import com.rapidmart.audit.entity.AuditLog;
import com.rapidmart.audit.repository.AuditLogRepository;
import com.rapidmart.common.enums.Role;
import com.rapidmart.common.exception.BusinessException;
import com.rapidmart.employee.service.EmployeeService;
import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmployeeManagerAuditTest {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

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

    private User manager;

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
        auditLogRepository.deleteAll();
        userRepository.deleteAll();

        manager = userRepository.save(new User("sysmgr@rapidmart.local", passwordEncoder.encode("Pass123!"), "System Manager", "+9477000", true, Set.of(Role.MANAGER)));
    }

    @Test
    void testManagerSelfDemotionAndSelfDeactivationBlocked() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(manager.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_MANAGER")))
        );

        // Attempt self-deactivation
        assertThrows(BusinessException.class, () ->
                employeeService.updateEmployeeRolesAndStatus(manager.getId(), Set.of(Role.MANAGER), false)
        );

        // Attempt self-demotion (removing MANAGER role)
        assertThrows(BusinessException.class, () ->
                employeeService.updateEmployeeRolesAndStatus(manager.getId(), Set.of(Role.CASHIER), true)
        );
    }

    @Test
    void testCreateEmployeeLogsAuditTrail() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(manager.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_MANAGER")))
        );

        User newEmp = employeeService.createEmployee("cashier1@rapidmart.local", "Pass123!", "Cashier One", "+947788", Set.of(Role.CASHIER));

        assertNotNull(newEmp.getId());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertFalse(logs.isEmpty());
        assertEquals("EMPLOYEE_CREATED", logs.get(0).getAction());
        assertEquals("sysmgr@rapidmart.local", logs.get(0).getActorEmail());
    }

    @Test
    void testCoordinatorCanManageDeliveryStaffOnlyAccounts() {
        User coordinator = userRepository.save(new User(
                "coordinator@rapidmart.local", passwordEncoder.encode("Pass123!"),
                "Delivery Coordinator", "+9477001", true, Set.of(Role.DELIVERY_COORDINATOR)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(coordinator.getEmail(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_DELIVERY_COORDINATOR")))
        );

        User driver = employeeService.createDeliveryStaff(
                "newdriver@rapidmart.local", "Pass123!", "New Driver", "+9477111");
        assertEquals(Set.of(Role.DELIVERY_STAFF), driver.getRoles());
        assertTrue(driver.isActive());
        assertTrue(passwordEncoder.matches("Pass123!", driver.getPasswordHash()));

        User updated = employeeService.updateDeliveryStaff(driver.getId(), "Updated Driver", "+9477222", false);
        assertEquals("Updated Driver", updated.getFullName());
        assertFalse(updated.isActive());
        assertEquals(Set.of(Role.DELIVERY_STAFF), updated.getRoles());

        User privilegedDriver = userRepository.save(new User(
                "manager-driver@rapidmart.local", passwordEncoder.encode("Pass123!"),
                "Manager Driver", null, true, Set.of(Role.MANAGER, Role.DELIVERY_STAFF)));
        assertThrows(BusinessException.class, () -> employeeService.updateDeliveryStaff(
                privilegedDriver.getId(), privilegedDriver.getFullName(), null, false));
    }
}
