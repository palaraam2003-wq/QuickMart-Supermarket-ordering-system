package com.rapidmart.auth;

import com.rapidmart.common.enums.Role;
import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Order(1)
public class DevDemoInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDemoInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${rapidmart.dev.seed-demo-manager:false}")
    private boolean seedDemoManager;

    public DevDemoInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!seedDemoManager) {
            return;
        }

        String demoEmail = "manager@rapidmart.local";
        if (!userRepository.existsByEmailIgnoreCase(demoEmail)) {
            User manager = new User(
                    demoEmail,
                    passwordEncoder.encode("DemoManager21!"),
                    "System Manager",
                    "+94770000000",
                    true,
                    Set.of(Role.MANAGER)
            );
            userRepository.save(manager);
            log.info("Demo Manager initialized successfully: {}", demoEmail);
        }
    }
}
