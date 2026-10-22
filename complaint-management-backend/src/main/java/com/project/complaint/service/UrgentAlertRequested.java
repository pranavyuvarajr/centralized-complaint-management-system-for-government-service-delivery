package com.project.complaint.service;

import java.util.List;

/**
 * Published inside the originating transaction; the email is sent after it commits, on the async pool.
 * Carries ids only: the listener reloads fresh data, so no phone number or token ever travels in an event.
 */
public record UrgentAlertRequested(Long complaintId, Kind kind, List<Long> recipientUserIds,
                                   int reminderNumber, int reminderTotal, long hoursPending) {
    public enum Kind { INITIAL, REMINDER, ESCALATION }
}
