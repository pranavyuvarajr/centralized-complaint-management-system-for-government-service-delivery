package com.project.complaint.security;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-memory per-user limiter for the location search / reverse-lookup
 * endpoints, so one browser (or script) can't hammer the free upstream
 * geocoding services on behalf of the whole app. Fixed one-minute window.
 */
@Component
public class GeocodeRateLimiter {

    private static final int MAX_REQUESTS_PER_WINDOW = 120;
    private static final long WINDOW_MS = 60_000;
    private static final int PRUNE_THRESHOLD = 5_000;

    private static final class Window {
        long start;
        int count;
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /** Returns true if the caller may proceed, false if they are over the limit for this window. */
    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        if (windows.size() > PRUNE_THRESHOLD) {
            windows.entrySet().removeIf(e -> now - e.getValue().start > WINDOW_MS);
        }
        Window w = windows.computeIfAbsent(key, k -> {
            Window created = new Window();
            created.start = now;
            return created;
        });
        synchronized (w) {
            if (now - w.start > WINDOW_MS) {
                w.start = now;
                w.count = 0;
            }
            if (w.count >= MAX_REQUESTS_PER_WINDOW) return false;
            w.count++;
            return true;
        }
    }
}
