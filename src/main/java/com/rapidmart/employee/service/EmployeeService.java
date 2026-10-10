package com.rapidmart.employee.service;

import com.rapidmart.audit.service.AuditService;
import com.rapidmart.common.enums.Role;
import com.rapidmart.common.exception.BusinessException;
import com.rapidmart.common.exception.ResourceNotFoundException;
import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.UserRepository;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class EmployeeService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public EmployeeService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    private User getAuthenticatedActor() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private static boolean hasStaffRole(Set<Role> roles) {
        return roles.stream().anyMatch(r -> r != Role.CUSTOMER);
    }

    private static void rejectManagerCustomerCombination(Set<Role> roles) {
        if (roles != null && roles.contains(Role.MANAGER) && roles.contains(Role.CUSTOMER)) {
            throw new BusinessException("INVALID_ROLES", "A manager cannot also hold the customer role.");
        }
    }

    /** Employees are users holding at least one non-customer role. */
    @Transactional(readOnly = true)
    public List<User> getAllEmployees() {
        return userRepository.findAll().stream()
                .filter(u -> hasStaffRole(u.getRoles()))
                .sorted(Comparator.comparing(User::getId))
                .toList();
    }

    @Transactional
    public User createEmployee(String email, String password, String fullName, String phone, Set<Role> roles) {
        User manager = getAuthenticatedActor();

        if (roles == null || !hasStaffRole(roles)) {
            throw new BusinessException("INVALID_ROLES", "An employee must have at least one staff role.");
        }
        rejectManagerCustomerCombination(roles);
        if (password == null || password.length() < 8) {
            throw new BusinessException("WEAK_PASSWORD", "Password must be at least 8 characters.");
        }
        String normalizedEmail = email.toLowerCase().trim();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BusinessException("EMAIL_EXISTS", "Employee email already exists.");
        }

        User employee = new User(
                normalizedEmail,
                passwordEncoder.encode(password),
                fullName.trim(),
                phone == null || phone.isBlank() ? null : phone.trim(),
                true,
                new HashSet<>(roles)
        );

        User saved = userRepository.save(employee);

        auditService.logAction(
                "EMPLOYEE_CREATED",
                manager.getEmail(),
                saved.getEmail(),
                "Created employee with roles: " + roles
        );

        return saved;
    }

    @Transactional
    public User updateEmployeeRolesAndStatus(Long employeeId, Set<Role> roles, boolean active) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", employeeId));
        return updateEmployee(employeeId, employee.getFullName(), employee.getPhone(), roles, active);
    }

    @Transactional
    public User updateEmployee(Long employeeId, String fullName, String phone, Set<Role> roles, boolean active) {
        User manager = getAuthenticatedActor();

        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", employeeId));

        if (roles == null || roles.isEmpty()) {
            throw new BusinessException("INVALID_ROLES", "At least one role is required.");
        }
        rejectManagerCustomerCombination(roles);
        if (fullName == null || fullName.isBlank()) {
            throw new BusinessException("INVALID_NAME", "Full name is required.");
        }

        // Self-Demotion and Self-Deactivation protection
        if (employee.getId().equals(manager.getId())) {
            if (!active) {
                throw new BusinessException("SELF_DEACTIVATION_PREVENTED", "Manager cannot deactivate their own account.");
            }
            if (!roles.contains(Role.MANAGER)) {
                throw new BusinessException("SELF_DEMOTION_PREVENTED", "Manager cannot remove their own MANAGER role.");
            }
        }

        boolean wasManager = employee.getRoles().contains(Role.MANAGER);
        boolean wasActive = employee.isActive();
        Set<Role> previousRoles = new HashSet<>(employee.getRoles());

        employee.setFullName(fullName.trim());
        employee.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        employee.setRoles(new HashSet<>(roles));
        employee.setActive(active);

        User saved = userRepository.save(employee);

        if (wasManager != roles.contains(Role.MANAGER)) {
            auditService.logAction(roles.contains(Role.MANAGER) ? "MANAGER_ACCESS_GRANTED" : "MANAGER_ACCESS_REVOKED",
                    manager.getEmail(), saved.getEmail(), "Roles changed from " + previousRoles + " to " + roles);
        }
        if (wasActive != active) {
            auditService.logAction(active ? "EMPLOYEE_ACTIVATED" : "EMPLOYEE_DEACTIVATED",
                    manager.getEmail(), saved.getEmail(), "Account active=" + active);
        }
        auditService.logAction(
                "EMPLOYEE_UPDATED",
                manager.getEmail(),
                saved.getEmail(),
                "Updated employee active=" + active + ", roles=" + roles
        );

        return saved;
    }

    /** Delivery coordinators may create accounts with only the DELIVERY_STAFF role. */
    @Transactional
    public User createDeliveryStaff(String email, String password, String fullName, String phone) {
        return createEmployee(email, password, fullName, phone, Set.of(Role.DELIVERY_STAFF));
    }

    /**
     * Delivery coordinators can update driver profile fields and account status, but cannot
     * change roles or manage a multi-role/privileged account through the coordinator API.
     */
    @Transactional
    public User updateDeliveryStaff(Long employeeId, String fullName, String phone, boolean active) {
        User employee = requireCoordinatorManagedDeliveryStaff(employeeId);
        return updateEmployee(employeeId, fullName, phone, employee.getRoles(), active);
    }

    @Transactional(readOnly = true)
    public User requireCoordinatorManagedDeliveryStaff(Long employeeId) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", employeeId));
        if (!employee.getRoles().equals(Set.of(Role.DELIVERY_STAFF))) {
            throw BusinessException.forbidden("Only delivery-staff-only accounts can be managed by a delivery coordinator.");
        }
        return employee;
    }
}
