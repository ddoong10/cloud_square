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

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_SECONDS = 300; // 5분

    private final ConcurrentHashMap<String, AtomicInteger> failedAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lockoutUntil = new ConcurrentHashMap<>();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EnvelopeEncryptionService envelopeEncryptionService;

    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        // 계정 잠금 확인
        Instant lockedUntil = lockoutUntil.get(email);
        if (lockedUntil != null && Instant.now().isBefore(lockedUntil)) {
            log.warn("Login blocked: account locked [{}]", MaskingUtils.maskEmail(email));
            throw new BadCredentialsException("Too many failed attempts. Try again later.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    recordFailedAttempt(email);
                    log.warn("Login failed: user not found [{}]", MaskingUtils.maskEmail(email));
                    return new BadCredentialsException("Invalid email or password");
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            recordFailedAttempt(email);
            log.warn("Login failed: bad password [{}]", MaskingUtils.maskEmail(email));
            throw new BadCredentialsException("Invalid email or password");
        }

        // 로그인 성공 시 실패 카운터 초기화
        failedAttempts.remove(email);
        lockoutUntil.remove(email);

        String token = jwtTokenProvider.createAccessToken(user);
        return new LoginResponse(
                "Bearer",
                token,
                jwtTokenProvider.getExpirationSeconds(),
                user.getEffectiveRole()
        );
    }

    private void recordFailedAttempt(String email) {
        int attempts = failedAttempts.computeIfAbsent(email, k -> new AtomicInteger(0)).incrementAndGet();
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            lockoutUntil.put(email, Instant.now().plusSeconds(LOCKOUT_DURATION_SECONDS));
            failedAttempts.remove(email);
            log.warn("Account locked for {} seconds: [{}]", LOCKOUT_DURATION_SECONDS, MaskingUtils.maskEmail(email));
        }
    }

    public SignupResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String residentNumber = normalizeResidentNumber(request.residentNumber());

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        String name = request.name() != null ? request.name().trim() : null;
        if (name != null && name.isEmpty()) {
            name = null;
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(name)
                .residentNumberEncrypted(envelopeEncryptionService.encrypt(residentNumber))
                .residentNumberHash(HashingUtils.sha256Hex(residentNumber))
                .role(UserRole.STUDENT)
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
