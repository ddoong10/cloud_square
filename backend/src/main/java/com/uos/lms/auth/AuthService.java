package com.uos.lms.auth;

import com.uos.lms.common.HashingUtils;
import com.uos.lms.common.MaskingUtils;
import com.uos.lms.kms.EnvelopeEncryptionService;
import com.uos.lms.security.JwtTokenProvider;
import com.uos.lms.user.User;
import com.uos.lms.user.UserRepository;
import com.uos.lms.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EnvelopeEncryptionService envelopeEncryptionService;

    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Login failed: user not found [{}]", MaskingUtils.maskEmail(email));
                    return new BadCredentialsException("Invalid email or password");
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failed: bad password [{}]", MaskingUtils.maskEmail(email));
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = jwtTokenProvider.createAccessToken(user);
        return new LoginResponse(
                "Bearer",
                token,
                jwtTokenProvider.getExpirationSeconds(),
                user.getEffectiveRole()
        );
    }

    public SignupResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String residentNumber = normalizeResidentNumber(request.residentNumber());

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .residentNumberEncrypted(envelopeEncryptionService.encrypt(residentNumber))
                .residentNumberHash(HashingUtils.sha256Hex(residentNumber))
                .role(UserRole.USER)
                .build();

        User savedUser = userRepository.save(user);
        log.info("Signup completed: [{}]", MaskingUtils.maskEmail(email));

        return new SignupResponse(savedUser.getId(), savedUser.getEmail(), savedUser.getEffectiveRole());
    }

    private String normalizeEmail(String email) {
        return email == null
                ? null
                : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeResidentNumber(String residentNumber) {
        if (residentNumber == null) {
            return null;
        }
        return residentNumber.replace("-", "").trim();
    }
}
