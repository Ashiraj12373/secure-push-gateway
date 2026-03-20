package com.securepushgateway.config;

import com.securepushgateway.model.User;
import com.securepushgateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.findByUsername("admin").isEmpty()) {
            userRepository.save(User.builder()
                .username("admin")
                .email("admin@securepush.io")
                .password(passwordEncoder.encode("admin123"))
                .role(User.Role.OWNER)
                .build());
            log.info("Default OWNER account created: admin / admin123");
        }
    }
}
