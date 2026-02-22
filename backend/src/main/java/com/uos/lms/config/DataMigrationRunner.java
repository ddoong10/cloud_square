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
            // MySQL ENUM 타입인 경우 새 값을 허용하도록 VARCHAR로 변환
            jdbcTemplate.execute(
                    "ALTER TABLE users MODIFY COLUMN role VARCHAR(20) NOT NULL"
            );
            log.info("Altered role column to VARCHAR(20)");
        } catch (Exception e) {
            log.debug("Column type alter skipped (may already be VARCHAR): {}", e.getMessage());
        }

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
