package com.project.complaint.util;

import java.util.regex.Pattern;

/**
 * Single place that defines what a valid mobile number is. Currently Indian
 * mobile numbers: 10 digits starting with 6-9, optionally written with a
 * +91 / 91 / 0 prefix and with spaces, dashes, dots or brackets, which are all
 * stripped. Numbers are stored in the normalized 10-digit form.
 * <p>
 * To support other countries, change {@link #VALID_MOBILE} and
 * {@link #normalize(String)} here; nothing else needs to know the format.
 */
public final class PhoneUtil {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-().]");
    private static final Pattern VALID_MOBILE = Pattern.compile("^[6-9]\\d{9}$");

    public static final String INVALID_MESSAGE =
            "Enter a valid 10-digit mobile number (it can start with +91).";

    public static final String DUPLICATE_MESSAGE =
            "This mobile number is already registered to another account. Use a different number.";

    private PhoneUtil() {}

    /** Returns the normalized number, or null when the input is not a valid mobile number. */
    public static String normalizeOrNull(String raw) {
        if (raw == null) return null;
        String s = SEPARATORS.matcher(raw.trim()).replaceAll("");
        if (s.startsWith("+91")) s = s.substring(3);
        else if (s.startsWith("91") && s.length() == 12) s = s.substring(2);
        else if (s.startsWith("0") && s.length() == 11) s = s.substring(1);
        return VALID_MOBILE.matcher(s).matches() ? s : null;
    }

    /** Returns the normalized number or throws IllegalStateException (reported to the client as a 400 with the message). */
    public static String requireValid(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("A mobile number is required. " + INVALID_MESSAGE);
        }
        String normalized = normalizeOrNull(raw);
        if (normalized == null) {
            throw new IllegalStateException(INVALID_MESSAGE);
        }
        return normalized;
    }
}
