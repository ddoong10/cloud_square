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
@Profile({"dev", "local"})
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

    @Value("${app.demo.instructor.email:instructor@lms.local}")
    private String demoInstructorEmail;

    @Value("${app.demo.instructor.password:instructor1234!}")
    private String demoInstructorPassword;

    @Override
    public void run(String... args) {
        seedDemoUsers();
    }

    private void seedDemoUsers() {
        createOrUpdateUser(demoUserEmail, demoUserPassword, UserRole.STUDENT, "데모 학생");
        createOrUpdateUser(demoAdminEmail, demoAdminPassword, UserRole.ADMIN, "관리자");
        createOrUpdateUser(demoInstructorEmail, demoInstructorPassword, UserRole.INSTRUCTOR, "데모 강사");
    }

    private void createOrUpdateUser(String email, String rawPassword, UserRole targetRole, String name) {
        userRepository.findByEmail(email).ifPresentOrElse(existingUser -> {
            boolean updated = false;
            if (existingUser.getRole() != targetRole) {
                existingUser.setRole(targetRole);
                updated = true;
            }
            if (existingUser.getName() == null || existingUser.getName().isBlank()) {
                existingUser.setName(name);
                updated = true;
            }
            if (updated) {
                userRepository.save(existingUser);
                log.info("Demo user updated: [{}] -> {}", MaskingUtils.maskEmail(email), targetRole.name());
            }
        }, () -> {
            User newUser = User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .name(name)
                    .role(targetRole)
                    .build();

            userRepository.save(newUser);
            log.info("Demo user created: [{}], role={}", MaskingUtils.maskEmail(email), targetRole.name());
        });
    }

}
