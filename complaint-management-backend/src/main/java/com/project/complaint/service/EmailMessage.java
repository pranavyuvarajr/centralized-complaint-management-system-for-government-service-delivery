package com.project.complaint.service;

/** A single outgoing email: plain-text body plus an optional simple HTML alternative. */
public record EmailMessage(String to, String subject, String textBody, String htmlBody) {
}
