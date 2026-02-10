package com.uos.lms.user;

import com.uos.lms.common.MaskingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.demo.user.email:demo@lms.local}")
    private String demoUserEmail;

    @Value("${app.demo.user.password:demo1234!}")
    private String demoUserPassword;

    @Value("${app.demo.admin.email:admin@lms.local}")
    private String demoAdminEmail;

    @Value("${app.demo.admin.password:admin1234!}")
    private String demoAdminPassword;

    @Override
    public void run(String... args) {
        seedDemoUsers();
    }

    private void seedDemoUsers() {
        createOrUpdateUser(demoUserEmail, demoUserPassword, UserRole.USER);
        createOrUpdateUser(demoAdminEmail, demoAdminPassword, UserRole.ADMIN);
    }

    private void createOrUpdateUser(String email, String rawPassword, UserRole targetRole) {
        userRepository.findByEmail(email).ifPresentOrElse(existingUser -> {
            boolean updated = false;
            if (existingUser.getRole() != targetRole) {
                existingUser.setRole(targetRole);
                updated = true;
            }
            if (updated) {
                userRepository.save(existingUser);
                log.info("Demo user role updated: [{}] -> {}", MaskingUtils.maskEmail(email), targetRole.name());
            }
        }, () -> {
            User newUser = User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .role(targetRole)
                    .build();

            userRepository.save(newUser);
            log.info("Demo user created: [{}], role={}", MaskingUtils.maskEmail(email), targetRole.name());
        });
    }

}
