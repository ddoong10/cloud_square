package com.uos.lms.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@Order(1)
@RequiredArgsConstructor
public class DataMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        migrateUserRoles();
    }

    private void migrateUserRoles() {
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE users SET role = 'STUDENT' WHERE role = 'USER'"
            );
            if (updated > 0) {
                log.info("Migrated {} user(s) from role USER to STUDENT", updated);
            }
        } catch (Exception e) {
            log.warn("Role migration skipped or failed: {}", e.getMessage());
        }
    }
}
