package com.rapidmart.customer.service;

import com.rapidmart.auth.AuthService;
import com.rapidmart.common.exception.ResourceNotFoundException;
import com.rapidmart.user.dto.UpdateProfileRequest;
import com.rapidmart.user.dto.UserResponseDto;
import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerProfileService {

    private final UserRepository userRepository;
    private final AuthService authService;

    public CustomerProfileService(UserRepository userRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    @Transactional(readOnly = true)
    public UserResponseDto getMyProfile() {
        return authService.mapToDto(getAuthenticatedUser());
    }

    @Transactional
    public UserResponseDto updateMyProfile(UpdateProfileRequest request) {
        User user = getAuthenticatedUser();
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        return authService.mapToDto(userRepository.save(user));
    }
}
