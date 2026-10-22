package com.project.complaint.entity;

/**
 * Verification state of an urgent complaint.
 * NONE = a normal complaint (never entered the urgent flow).
 * PENDING = marked urgent by the citizen, not yet verified by staff.
 * CONFIRMED_URGENT / DOWNGRADED_TO_NORMAL = locked decisions (only an admin override can change them).
 */
public enum UrgencyStatus {
    NONE,
    PENDING,
    CONFIRMED_URGENT,
    DOWNGRADED_TO_NORMAL
}
