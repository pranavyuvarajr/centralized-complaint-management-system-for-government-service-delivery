package com.project.complaint.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight in-memory rate limiter for the login endpoint.
 * Blocks an email address after too many failed attempts within a time window.
 * Resets automatically after the window expires or on a successful login.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 300; // 5 minutes

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    private static class Attempt {
        final AtomicInteger count = new AtomicInteger(0);
        volatile Instant windowStart = Instant.now();
    }

    public void checkAllowed(String key) {
        Attempt attempt = attempts.computeIfAbsent(key, k -> new Attempt());
        Instant now = Instant.now();
        if (now.getEpochSecond() - attempt.windowStart.getEpochSecond() > WINDOW_SECONDS) {
            attempt.count.set(0);
            attempt.windowStart = now;
        }
        if (attempt.count.get() >= MAX_ATTEMPTS) {
            throw new RuntimeException("Too many login attempts. Please try again in a few minutes.");
        }
    }

    public void recordFailure(String key) {
        Attempt attempt = attempts.computeIfAbsent(key, k -> new Attempt());
        attempt.count.incrementAndGet();
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }
}
