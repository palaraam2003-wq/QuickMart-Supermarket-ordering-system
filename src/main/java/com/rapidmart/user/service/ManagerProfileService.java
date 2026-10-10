package com.rapidmart.user.service;

import com.rapidmart.audit.service.AuditService;
import com.rapidmart.auth.AuthService;
import com.rapidmart.common.enums.EmailChangeStatus;
import com.rapidmart.common.exception.BusinessException;
import com.rapidmart.common.exception.ResourceNotFoundException;
import com.rapidmart.user.dto.ChangePasswordRequest;
import com.rapidmart.user.dto.EmailChangeRequestDto;
import com.rapidmart.user.dto.UpdateProfileRequest;
import com.rapidmart.user.dto.UserResponseDto;
import com.rapidmart.user.entity.EmailChangeRequest;
import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.EmailChangeRequestRepository;
import com.rapidmart.user.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Self-service profile for the signed-in manager: view, edit name/phone, request an email change, change password. */
@Service
public class ManagerProfileService {

    private final UserRepository userRepository;
    private final EmailChangeRequestRepository emailChangeRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final AuditService auditService;

    public ManagerProfileService(UserRepository userRepository,
                                 EmailChangeRequestRepository emailChangeRequestRepository,
                                 PasswordEncoder passwordEncoder,
                                 AuthService authService,
                                 AuditService auditService) {
        this.userRepository = userRepository;
        this.emailChangeRequestRepository = emailChangeRequestRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.auditService = auditService;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    @Transactional(readOnly = true)
    public UserResponseDto getProfile() {
        return authService.mapToDto(getAuthenticatedUser());
    }

    @Transactional
    public UserResponseDto updateProfile(UpdateProfileRequest request) {
        User user = getAuthenticatedUser();
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        User saved = userRepository.save(user);
        auditService.logAction("MANAGER_PROFILE_UPDATED", saved.getEmail(), saved.getEmail(), "Updated name and phone");
        return authService.mapToDto(saved);
    }

    @Transactional(readOnly = true)
    public Optional<EmailChangeRequestDto> getPendingEmailChange() {
        User user = getAuthenticatedUser();
        return emailChangeRequestRepository
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), EmailChangeStatus.PENDING)
                .map(EmailChangeRequestDto::from);
    }

    /** Records a pending request; the login email is not changed until it is approved. A new request replaces an older pending one. */
    @Transactional
    public EmailChangeRequestDto requestEmailChange(String newEmail) {
        User user = getAuthenticatedUser();
        String normalized = newEmail.toLowerCase().trim();

        if (normalized.equalsIgnoreCase(user.getEmail())) {
            throw new BusinessException("EMAIL_UNCHANGED", "The new email is the same as your current email.");
        }
        if (userRepository.existsByEmailIgnoreCase(normalized)) {
            throw new BusinessException("EMAIL_ALREADY_EXISTS", "Email is already registered.");
        }

        emailChangeRequestRepository.findByUserIdAndStatus(user.getId(), EmailChangeStatus.PENDING)
                .forEach(old -> old.setStatus(EmailChangeStatus.CANCELLED));

        EmailChangeRequest saved = emailChangeRequestRepository.save(new EmailChangeRequest(user, normalized));
        auditService.logAction("EMAIL_CHANGE_REQUESTED", user.getEmail(), user.getEmail(), "Requested change to " + normalized);
        return EmailChangeRequestDto.from(saved);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = getAuthenticatedUser();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("INVALID_CURRENT_PASSWORD", "Current password is incorrect.");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException("PASSWORD_MISMATCH", "New password and confirmation do not match.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException("PASSWORD_UNCHANGED", "New password must be different from the current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        auditService.logAction("PASSWORD_CHANGED", user.getEmail(), user.getEmail(), "Manager changed their password");
    }
}
