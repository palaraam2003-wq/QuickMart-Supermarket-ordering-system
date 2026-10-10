package com.rapidmart.auth;

import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class ActiveUserCheckFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public ActiveUserCheckFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser")) {
            String email = authentication.getName();
            Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
            var session = request.getSession(false);
            if (userOpt.isPresent() && session != null
                    && session.getAttribute("LOGIN_PASSWORD_HASH") != null
                    && !userOpt.get().getPasswordHash().equals(session.getAttribute("LOGIN_PASSWORD_HASH"))) {
                session.invalidate();
                SecurityContextHolder.clearContext();
                response.sendError(401, "Password changed. Please sign in again.");
                return;
            }

            if (userOpt.isPresent() && !userOpt.get().isActive()) {
                SecurityContextHolder.clearContext();
                if (request.getSession(false) != null) {
                    request.getSession().invalidate();
                }
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json");
                response.getWriter().write(String.format("""
                    {
                        "timestamp": "%s",
                        "status": 400,
                        "errorCode": "ACCOUNT_DISABLED",
                        "message": "User account is deactivated.",
                        "path": "%s"
                    }
                    """, java.time.OffsetDateTime.now(), request.getRequestURI()));
                return;
            } else if (userOpt.isEmpty()) {
                SecurityContextHolder.clearContext();
                if (request.getSession(false) != null) {
                    request.getSession().invalidate();
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
