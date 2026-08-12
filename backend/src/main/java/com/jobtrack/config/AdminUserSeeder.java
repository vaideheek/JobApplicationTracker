package com.jobtrack.config;

import com.jobtrack.entity.User;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.repository.JobApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminUserSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.admin.username:${app.admin.email:owner}}")
    private String adminUsername;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.demo.username:demo}")
    private String demoUsername;

    @Value("${app.demo.password:}")
    private String demoPassword;

    @Override
    @Transactional
    public void run(String... args) {
        // 1. Synchronize identity sequence dynamically to avoid duplicate key conflicts (fails fast if sequence sync fails)
        syncIdentitySequence();

        User realAdmin = null;

        // 2. Seed owner user if not exists and password is provided
        if (adminUsername != null && !adminUsername.isBlank() && adminPassword != null && !adminPassword.isBlank()) {
            String usernameNorm = adminUsername.trim().toLowerCase();
            realAdmin = userRepository.findByUsernameIgnoreCase(usernameNorm).orElse(null);

            if (realAdmin == null) {
                log.info("Seeding configured administrator: {}", usernameNorm);
                realAdmin = User.builder()
                        .username(usernameNorm)
                        .passwordHash(passwordEncoder.encode(adminPassword))
                        .displayName("Primary Owner")
                        .role("ROLE_USER")
                        .enabled(true)
                        .demoAccount(false)
                        .createdAt(LocalDateTime.now())
                        .build();
                realAdmin = userRepository.save(realAdmin);
            } else {
                log.info("Configured administrator {} already exists.", usernameNorm);
            }
        } else {
            log.warn("Owner credentials are empty or not configured. Skipping owner seeding.");
        }

        // 3. Seed demo user if not exists and password is provided
        if (demoUsername != null && !demoUsername.isBlank() && demoPassword != null && !demoPassword.isBlank()) {
            String demoNorm = demoUsername.trim().toLowerCase();
            if (!userRepository.existsByUsernameIgnoreCase(demoNorm)) {
                log.info("Seeding demo user: {}", demoNorm);
                User demo = User.builder()
                        .username(demoNorm)
                        .passwordHash(passwordEncoder.encode(demoPassword))
                        .displayName("Demo User")
                        .role("ROLE_USER")
                        .enabled(true)
                        .demoAccount(true)
                        .createdAt(LocalDateTime.now())
                        .build();
                userRepository.save(demo);
            } else {
                log.info("Demo user {} already exists. Skipping seeding.", demoNorm);
            }
        }

        // 4. Fallback Reassignment: If migration placeholder owner exists, reassign apps to real admin and delete placeholder
        User placeholder = userRepository.findByUsernameIgnoreCase("migration_placeholder").orElse(null);
        if (placeholder != null) {
            log.info("Found migration placeholder owner. Processing transition...");
            if (realAdmin != null) {
                log.info("Reassigning applications from placeholder to administrator: {}", realAdmin.getUsername());
                jobApplicationRepository.reassignApplications(placeholder.getId(), realAdmin);
                userRepository.delete(placeholder);
                log.info("Placeholder owner successfully removed.");
            } else {
                log.error("Migration placeholder exists but no real administrator is configured to take ownership!");
            }
        }
    }

    private void syncIdentitySequence() {
        String dbType = jdbcTemplate.execute((Connection conn) -> conn.getMetaData().getDatabaseProductName());
        log.info("Syncing users identity sequence for database engine: {}", dbType);

        if ("PostgreSQL".equalsIgnoreCase(dbType)) {
            // Unambiguous Postgres next-value setup equivalent to max(id) + 1 with is_called = false
            jdbcTemplate.execute("SELECT setval(pg_get_serial_sequence('users', 'id'), COALESCE((SELECT MAX(id) FROM users), 0) + 1, false);");
            log.info("PostgreSQL users sequence synchronized successfully.");
        } else if ("H2".equalsIgnoreCase(dbType)) {
            jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN id RESTART WITH (SELECT COALESCE(MAX(id) + 1, 1) FROM users);");
            log.info("H2 users sequence synchronized successfully.");
        } else {
            throw new UnsupportedOperationException("Database engine '" + dbType + "' is not supported for users identity sequence synchronization.");
        }
    }
}
