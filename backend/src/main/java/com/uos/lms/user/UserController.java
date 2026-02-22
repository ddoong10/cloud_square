package com.uos.lms.user;

import com.uos.lms.kms.EnvelopeEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final EnvelopeEncryptionService envelopeEncryptionService;

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        User user = getCurrentUser(authentication);
        return new MeResponse(user.getId(), user.getEmail(), user.getName(), user.getEffectiveRole());
    }

    @GetMapping("/me/resident-number")
    public ResidentNumberResponse residentNumber(Authentication authentication) {
        User user = getCurrentUser(authentication);
        String residentEncrypted = user.getResidentNumberEncrypted();
        boolean available = residentEncrypted != null && !residentEncrypted.isBlank();
        boolean encryptedAtRest = envelopeEncryptionService.isEnvelopeValue(residentEncrypted);

        if (!available) {
            return new ResidentNumberResponse(user.getId(), null, encryptedAtRest, false);
        }

        String residentPlain = envelopeEncryptionService.decrypt(residentEncrypted);
        return new ResidentNumberResponse(
                user.getId(),
                formatResidentNumberForDisplay(residentPlain),
                encryptedAtRest,
                true
        );
    }

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BadCredentialsException("Unauthorized");
        }

        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Unauthorized"));
    }

    private String formatResidentNumberForDisplay(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("-", "").trim();
        if (normalized.length() == 13) {
            return normalized.substring(0, 6) + "-" + normalized.substring(6);
        }
        return normalized;
    }
}
