package com.project.complaint.controller;

import com.project.complaint.exception.GeocoderUnavailableException;
import com.project.complaint.security.GeocodeRateLimiter;
import com.project.complaint.service.GeoLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backs the location picker on the complaint form: address/street search,
 * and reverse lookup (coordinates -> readable address) for the pin and the
 * "use my current location" button.
 */
@RestController
@RequestMapping("/api/geocode")
@RequiredArgsConstructor
public class GeoLocationController {

    private final GeoLocationService geoLocationService;
    private final GeocodeRateLimiter rateLimiter;

    /**
     * lat/lon are optional and only bias results toward where the user is
     * looking (map center / current position). 200 with an empty list means
     * "no matches"; 503 means the geocoding services are unreachable.
     */
    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String q,
                                    @RequestParam(required = false) Double lat,
                                    @RequestParam(required = false) Double lon,
                                    Authentication auth) {
        if (!rateLimiter.tryAcquire(auth.getName())) {
            return error(HttpStatus.TOO_MANY_REQUESTS, "Too many searches. Please wait a moment and try again.");
        }
        try {
            return ResponseEntity.ok(geoLocationService.search(q, lat, lon));
        } catch (GeocoderUnavailableException e) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    /** Returns { "address": "..." }, or { "address": null } when no address could be found. */
    @GetMapping("/reverse")
    public ResponseEntity<?> reverse(@RequestParam double lat, @RequestParam double lon, Authentication auth) {
        if (!geoLocationService.isValidCoordinate(lat, lon)) {
            return error(HttpStatus.BAD_REQUEST, "Invalid coordinates.");
        }
        if (!rateLimiter.tryAcquire(auth.getName())) {
            return error(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please wait a moment and try again.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("address", geoLocationService.reverseLookup(lat, lon).orElse(null));
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
