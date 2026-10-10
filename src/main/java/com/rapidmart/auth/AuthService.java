package com.rapidmart.auth;

import com.rapidmart.common.enums.Role;
import com.rapidmart.common.exception.BusinessException;
import com.rapidmart.common.exception.ResourceNotFoundException;

import com.rapidmart.user.dto.LoginRequest;
import com.rapidmart.user.dto.RegisterUserRequest;
import com.rapidmart.user.dto.UserResponseDto;
import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Set;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public UserResponseDto registerCustomer(RegisterUserRequest request) {
        request.setEmail(LoginEmailRules.validate(request.getEmail(), false));
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new BusinessException("EMAIL_ALREADY_EXISTS", "Email is already registered.");
        }

        User user = new User(
                request.getEmail().toLowerCase().trim(),
                passwordEncoder.encode(request.getPassword()),
                request.getFullName(),
                request.getPhone(),
                true,
                Set.of(Role.CUSTOMER)
        );

        User savedUser = userRepository.save(user);
        return mapToDto(savedUser);
    }

    public UserResponseDto login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String portal = request.getPortal();
        if (!"staff".equals(portal) && !"customer".equals(portal)) {
            throw new BusinessException("INVALID_PORTAL", "Select staff or customer login.");
        }
        String email = LoginEmailRules.validate(request.getEmail(), "staff".equals(portal));
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        User portalUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        boolean customerOnly = portalUser.getRoles().equals(Set.of(Role.CUSTOMER));
        if (("customer".equals(portal) && !customerOnly)
                || ("staff".equals(portal) && (customerOnly || portalUser.getRoles().isEmpty()))) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "This account cannot sign in to this portal.");
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // Session fixation protection: rotate the session id of any pre-login session
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        session.setAttribute("LOGIN_PASSWORD_HASH", portalUser.getPasswordHash());

        User user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", authentication.getName()));

        user.setLastLoginAt(OffsetDateTime.now());
        user = userRepository.save(user);

        return mapToDto(user);
    }

    public void logout(HttpServletRequest httpRequest) {
        SecurityContextHolder.clearContext();
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public UserResponseDto getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName().equals("anonymousUser")) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "NOT_AUTHENTICATED", "User is not authenticated.");
        }

        User user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", authentication.getName()));

        if (!user.isActive()) {
            throw new BusinessException("ACCOUNT_DISABLED", "User account is deactivated.");
        }

        return mapToDto(user);
    }

    public UserResponseDto mapToDto(User user) {
        UserResponseDto dto = new UserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.isActive(),
                user.getRoles(),
                user.getCreatedAt()
        );
        dto.setLastLoginAt(user.getLastLoginAt());
        return dto;
    }
}
