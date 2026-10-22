package com.project.complaint.service;

/** Published inside the transaction; the verification mail is sent after it commits. The raw token is never stored. */
public record AlertEmailVerificationRequested(String toAddress, String recipientName, String rawToken) {
}
