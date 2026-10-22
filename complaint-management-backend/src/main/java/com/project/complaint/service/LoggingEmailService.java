package com.project.complaint.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Log-only mode (app.mail.enabled=false, the default): logs the would-be email, including its link. */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailService implements EmailService {

    @Override
    public void send(EmailMessage message) {
        log.info("[mail disabled] Would send email to={} subject=\"{}\"\n{}",
                message.to(), message.subject(), message.textBody());
    }
}
