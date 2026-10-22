package com.project.complaint.service;

/**
 * Provider-neutral outgoing mail. Callers depend only on this interface, so the SMTP implementation
 * can be swapped (for example for an HTTP mail API) without touching them. Implementations may throw;
 * callers that must not fail because of mail (alerts, verification) catch and log.
 */
public interface EmailService {
    void send(EmailMessage message);
}
