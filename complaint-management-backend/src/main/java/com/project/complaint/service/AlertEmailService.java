package com.project.complaint.service;

import com.project.complaint.dto.AlertEmailDto;
import com.project.complaint.entity.Role;
import com.project.complaint.entity.User;
import com.project.complaint.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Alert email for OFFICIAL and ADMIN accounts. Only a set AND verified address is ever used for urgent
 * alerts (no fallback to the login email); an unverified address counts as unset everywhere.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertEmailService {

    static final Duration TOKEN_VALIDITY = Duration.ofHours(24);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** The address to mail, or null when none is set or it is not verified. */
    public static String verifiedAlertEmail(User user) {
        if (user == null || !user.isAlertEmailVerified()) return null;
        String a = user.getAlertEmail();
        return (a == null || a.isBlank()) ? null : a;
    }

    public static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public AlertEmailDto.Status set(String email, String rawAddress) {
        User user = staffUser(email);
        String address = rawAddress == null ? "" : rawAddress.trim().toLowerCase();
        if (address.length() > 254 || !EMAIL.matcher(address).matches()) {
            throw new RuntimeException("Please enter a valid email address.");
        }
        if (address.equals(user.getAlertEmail()) && user.isAlertEmailVerified()) {
            return status(user); // already verified, nothing to do
        }
        enforceCooldown(user);
        user.setAlertEmail(address);
        user.setAlertEmailVerified(false); // changing the address always resets verification
        issueToken(user);
        return status(userRepository.save(user));
    }

    @Transactional
    public AlertEmailDto.Status resend(String email) {
        User user = staffUser(email);
        if (user.getAlertEmail() == null || user.getAlertEmail().isBlank()) {
            throw new RuntimeException("No alert email is set.");
        }
        if (user.isAlertEmailVerified()) {
            throw new RuntimeException("This alert email is already verified.");
        }
        enforceCooldown(user);
        issueToken(user);
        return status(userRepository.save(user));
    }

    @Transactional
    public AlertEmailDto.Status clear(String email) {
        User user = staffUser(email);
        user.setAlertEmail(null);
        user.setAlertEmailVerified(false);
        user.setAlertEmailTokenHash(null);
        user.setAlertEmailTokenExpiresAt(null);
        // alertEmailTokenSentAt is kept on purpose so clear/set cannot be used to bypass the resend cooldown
        return status(userRepository.save(user));
    }

    /** Public endpoint: single-use, 24h token. Invalid, expired and used tokens look the same. */
    @Transactional
    public void verify(String rawToken) {
        String bad = "This verification link is invalid or has expired. Request a new one from your profile page.";
        if (rawToken == null || rawToken.isBlank()) throw new RuntimeException(bad);
        User user = userRepository.findByAlertEmailTokenHash(sha256Hex(rawToken.trim()))
                .orElseThrow(() -> new RuntimeException(bad));
        LocalDateTime now = LocalDateTime.now(clock);
        if (user.getAlertEmailTokenExpiresAt() == null || !now.isBefore(user.getAlertEmailTokenExpiresAt())
                || user.getAlertEmail() == null) {
            throw new RuntimeException(bad);
        }
        user.setAlertEmailVerified(true);
        user.setAlertEmailTokenHash(null);       // single use
        user.setAlertEmailTokenExpiresAt(null);
        userRepository.save(user);
    }

    private User staffUser(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getRole() != Role.OFFICIAL && user.getRole() != Role.ADMIN) {
            throw new RuntimeException("Only officials and administrators can set an alert email.");
        }
        return user;
    }

    private void enforceCooldown(User user) {
        LocalDateTime sent = user.getAlertEmailTokenSentAt();
        if (sent != null) {
            long wait = RESEND_COOLDOWN.minus(Duration.between(sent, LocalDateTime.now(clock))).toSeconds();
            if (wait > 0) {
                throw new RuntimeException("Please wait " + (wait + 1) + " seconds before requesting another verification email.");
            }
        }
    }

    private void issueToken(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime now = LocalDateTime.now(clock);
        user.setAlertEmailTokenHash(sha256Hex(raw));
        user.setAlertEmailTokenExpiresAt(now.plus(TOKEN_VALIDITY));
        user.setAlertEmailTokenSentAt(now);
        events.publishEvent(new AlertEmailVerificationRequested(user.getAlertEmail(), user.getName(), raw));
    }

    private AlertEmailDto.Status status(User user) {
        return AlertEmailDto.Status.builder()
                .alertEmail(user.getAlertEmail())
                .alertEmailVerified(user.isAlertEmailVerified())
                .build();
    }
}
