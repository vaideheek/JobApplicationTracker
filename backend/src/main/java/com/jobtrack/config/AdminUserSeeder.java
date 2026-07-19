package com.jobtrack.config;

import com.jobtrack.entity.AppUser;
import com.jobtrack.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminUserSeeder implements CommandLineRunner {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.warn("Admin credentials are empty in environment configuration. Seeding skipped.");
            return;
        }

        if (userRepository.count() == 0) {
            log.info("No users found in database. Seeding default administrator: {}", adminEmail);
            AppUser admin = AppUser.builder()
                    .email(adminEmail.trim().toLowerCase())
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .role("ROLE_ADMIN")
                    .enabled(true)
                    .build();
            userRepository.save(admin);
        } else {
            log.info("Users table is not empty. Skipping default administrator seeding.");
        }
    }
}
