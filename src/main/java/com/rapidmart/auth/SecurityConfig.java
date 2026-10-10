package com.rapidmart.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final ActiveUserCheckFilter activeUserCheckFilter;

    public SecurityConfig(CustomAuthenticationEntryPoint authenticationEntryPoint,
                          CustomAccessDeniedHandler accessDeniedHandler,
                          ActiveUserCheckFilter activeUserCheckFilter) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.activeUserCheckFilter = activeUserCheckFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName("_csrf");

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(requestHandler)
            )
            .headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                // Public APIs
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/products/public/**").permitAll()
                .requestMatchers("/api/v1/categories/public/**").permitAll()

                // Role-Specific APIs
                .requestMatchers("/api/v1/cashier/**").hasAnyRole("CASHIER", "MANAGER")
                // Restock separation of duties (also enforced by @PreAuthorize on the controller and service):
                // Inventory Officers (INVENTORY_STAFF) create and receive, Managers approve and reject.
                .requestMatchers(HttpMethod.POST, "/api/v1/inventory/restock-requests",
                        "/api/v1/inventory/restock-requests/*/receive").hasRole("INVENTORY_STAFF")
                .requestMatchers(HttpMethod.PUT, "/api/v1/inventory/restock-requests/*/approve",
                        "/api/v1/inventory/restock-requests/*/reject").hasRole("MANAGER")
                .requestMatchers("/api/v1/inventory/**").hasAnyRole("INVENTORY_STAFF", "MANAGER")
                .requestMatchers("/api/v1/delivery-coordinator/**").hasAnyRole("DELIVERY_COORDINATOR", "MANAGER")
                .requestMatchers("/api/v1/delivery-staff/**").hasRole("DELIVERY_STAFF")
                .requestMatchers("/api/deliveries/**", "/api/v1/deliveries/**").hasAnyRole("DELIVERY_STAFF", "DELIVERY_COORDINATOR", "MANAGER")
                .requestMatchers("/api/v1/loyalty-officer/**").hasAnyRole("LOYALTY_OFFICER", "MANAGER")
                .requestMatchers("/api/v1/manager/**").hasRole("MANAGER")
                .requestMatchers("/api/v1/customer/**", "/api/customer/**").hasRole("CUSTOMER")

                // Static Web Assets & Pages (Authority enforced at API boundary)
                .requestMatchers("/", "/*.html", "/*/*.html", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()

                .anyRequest().authenticated()
            )
            .addFilterBefore(activeUserCheckFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
