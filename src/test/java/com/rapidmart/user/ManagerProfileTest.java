package com.rapidmart.user;

import com.rapidmart.auth.AuthService;
import com.rapidmart.common.enums.EmailChangeStatus;
import com.rapidmart.common.enums.Role;
import com.rapidmart.common.exception.BusinessException;
import com.rapidmart.employee.service.EmployeeService;
import com.rapidmart.user.dto.ChangePasswordRequest;
import com.rapidmart.user.dto.EmailChangeRequestDto;
import com.rapidmart.user.dto.LoginRequest;
import com.rapidmart.user.dto.UpdateProfileRequest;
import com.rapidmart.user.dto.UserResponseDto;
import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.EmailChangeRequestRepository;
import com.rapidmart.user.repository.UserRepository;
import com.rapidmart.user.service.ManagerProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ManagerProfileTest {

    @Autowired
    private ManagerProfileService profileService;
    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EmailChangeRequestRepository emailChangeRequestRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User manager;
    private String managerEmail;

    @BeforeEach
    void setUp() {
        // Unique address per test: the shared in-memory DB may hold rows from other test classes.
        managerEmail = "profilemgr-" + UUID.randomUUID() + "@rapidmart.local";
        manager = userRepository.save(new User(managerEmail, passwordEncoder.encode("OldPass123!"),
                "Profile Manager", "+94770000001", true, new HashSet<>(Set.of(Role.MANAGER))));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                manager.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
    }

    @Test
    void updatesNameAndPhoneButNotEmail() {
        UserResponseDto updated = profileService.updateProfile(new UpdateProfileRequest("  New Name ", "+94771234567"));

        assertEquals("New Name", updated.getFullName());
        assertEquals("+94771234567", updated.getPhone());
        assertEquals(managerEmail, updated.getEmail());
    }

    @Test
    void emailChangeIsRecordedAsPendingAndLoginEmailStaysTheSame() {
        EmailChangeRequestDto request = profileService.requestEmailChange("New.Address@RapidMart.local");

        assertEquals("new.address@rapidmart.local", request.newEmail());
        assertEquals(EmailChangeStatus.PENDING, request.status());
        assertEquals(managerEmail, userRepository.findById(manager.getId()).orElseThrow().getEmail());
        assertTrue(profileService.getPendingEmailChange().isPresent());
    }

    @Test
    void newEmailRequestReplacesEarlierPendingOne() {
        profileService.requestEmailChange("first@rapidmart.local");
        profileService.requestEmailChange("second@rapidmart.local");

        assertEquals("second@rapidmart.local", profileService.getPendingEmailChange().orElseThrow().newEmail());
        assertEquals(1, emailChangeRequestRepository.findByUserIdAndStatus(manager.getId(), EmailChangeStatus.PENDING).size());
    }

    @Test
    void emailChangeRejectsSameOrTakenEmail() {
        String takenEmail = "taken-" + UUID.randomUUID() + "@rapidmart.local";
        userRepository.save(new User(takenEmail, "pass", "Other", null, true, Set.of(Role.CASHIER)));

        assertEquals("EMAIL_UNCHANGED", assertThrows(BusinessException.class,
                () -> profileService.requestEmailChange(managerEmail.toUpperCase())).getErrorCode());
        assertEquals("EMAIL_ALREADY_EXISTS", assertThrows(BusinessException.class,
                () -> profileService.requestEmailChange(takenEmail)).getErrorCode());
    }

    @Test
    void passwordChangeRequiresCorrectCurrentPasswordAndMatchingConfirmation() {
        assertEquals("INVALID_CURRENT_PASSWORD", assertThrows(BusinessException.class,
                () -> profileService.changePassword(new ChangePasswordRequest("wrong", "NewPass123!", "NewPass123!"))).getErrorCode());
        assertEquals("PASSWORD_MISMATCH", assertThrows(BusinessException.class,
                () -> profileService.changePassword(new ChangePasswordRequest("OldPass123!", "NewPass123!", "Different123!"))).getErrorCode());
        assertEquals("PASSWORD_UNCHANGED", assertThrows(BusinessException.class,
                () -> profileService.changePassword(new ChangePasswordRequest("OldPass123!", "OldPass123!", "OldPass123!"))).getErrorCode());

        profileService.changePassword(new ChangePasswordRequest("OldPass123!", "NewPass123!", "NewPass123!"));

        User reloaded = userRepository.findById(manager.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("NewPass123!", reloaded.getPasswordHash()));
        assertFalse(passwordEncoder.matches("OldPass123!", reloaded.getPasswordHash()));
    }

    @Test
    void loginRecordsLastLoginTime() {
        assertNull(profileService.getProfile().getLastLoginAt());

        LoginRequest login = new LoginRequest();
        login.setEmail(managerEmail);
        login.setPassword("OldPass123!");
        login.setPortal("staff");
        authService.login(login, new MockHttpServletRequest(), new MockHttpServletResponse());

        assertNotNull(profileService.getProfile().getLastLoginAt());
    }

    @Test
    void managerCannotBeGivenTheCustomerRole() {
        assertEquals("INVALID_ROLES", assertThrows(BusinessException.class, () -> employeeService.createEmployee(
                "mgr2-" + UUID.randomUUID() + "@rapidmart.local", "Pass1234!", "Second Manager", null, Set.of(Role.MANAGER, Role.CUSTOMER))).getErrorCode());
    }
}
