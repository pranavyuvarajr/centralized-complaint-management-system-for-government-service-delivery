package com.project.complaint.exception;

/**
 * Thrown when every configured geocoding provider failed (network error,
 * timeout, rate limit, ...). Distinct from "no results" so the UI can tell the
 * user search is temporarily down instead of claiming nothing exists.
 */
public class GeocoderUnavailableException extends RuntimeException {
    public GeocoderUnavailableException(String message) {
        super(message);
    }
}
