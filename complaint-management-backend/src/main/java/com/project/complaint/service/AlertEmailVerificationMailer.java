package com.project.complaint.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends the verification email after commit; a mail failure is logged and never reaches the caller. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEmailVerificationMailer {

    private final EmailService emailService;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Async("alertTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRequested(AlertEmailVerificationRequested event) {
        try {
            String base = frontendBaseUrl.endsWith("/") ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
            String link = base + "/verify-alert-email?token=" + event.rawToken();
            String text = "Hello " + event.recipientName() + ",\n\n"
                    + "Please confirm this address as your alert email for urgent-complaint notifications "
                    + "in the Complaint Management System:\n\n" + link + "\n\n"
                    + "The link is valid for 24 hours and can be used once. If you did not request this, you can ignore this email.\n";
            String html = "<p>Hello " + escape(event.recipientName()) + ",</p>"
                    + "<p>Please confirm this address as your alert email for urgent-complaint notifications "
                    + "in the Complaint Management System:</p>"
                    + "<p><a href=\"" + link + "\">Confirm alert email</a></p>"
                    + "<p>The link is valid for 24 hours and can be used once. If you did not request this, you can ignore this email.</p>";
            emailService.send(new EmailMessage(event.toAddress(), "Confirm your alert email", text, html));
        } catch (Exception e) {
            log.warn("Could not send alert-email verification mail: {}", e.getMessage());
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
