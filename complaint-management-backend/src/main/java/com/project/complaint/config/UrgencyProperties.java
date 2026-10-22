package com.project.complaint.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Reminder schedule for PENDING urgent complaints. Defaults are the production schedule
 * (reminders at +6h, +12h, +18h, +24h, admin escalation 5 minutes after the 4th). Shorten them
 * through environment variables to test quickly, e.g. URGENCY_REMINDER_INTERVAL=PT1M.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.urgency")
public class UrgencyProperties {
    /** Time between reminders; reminder n is due n * interval after the clock start. */
    private Duration reminderInterval = Duration.ofHours(6);
    /** How long after the last reminder the one-time admin escalation is sent. */
    private Duration escalationDelay = Duration.ofMinutes(5);
    /** Number of reminders to the current assignee before escalation. */
    private int reminderCount = 4;
}
