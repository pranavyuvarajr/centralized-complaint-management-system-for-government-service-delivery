package com.project.complaint.service;

import com.project.complaint.config.UrgencyProperties;
import com.project.complaint.entity.Complaint;
import com.project.complaint.entity.Role;
import com.project.complaint.entity.User;
import com.project.complaint.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides WHO is alerted and HOW for an urgent complaint, and publishes the after-commit email event.
 * Must be called inside the caller's transaction (it modifies the managed complaint and writes in-app
 * notifications). Recipient rule: a verified alert email gets an email (email only);
 * a recipient without one gets an in-app notification (except the initial alert to an
 * assigned official, whose in-app message is the URGENT-worded assignment notification the caller already sends).
 */
@Service
@RequiredArgsConstructor
public class UrgentAlertService {

    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher events;
    private final UrgencyProperties properties;
    private final Clock clock;

    /** Citizen just submitted an urgent complaint (after auto-routing ran). Starts the reminder clock. */
    public void onSubmitted(Complaint c) {
        c.setUrgencyAssignedAt(LocalDateTime.now(clock));
        alertInitial(c);
    }

    /** A PENDING urgent complaint was assigned by an admin, or handed over: fresh reminders, same alert as auto-assign. */
    public void onReassigned(Complaint c) {
        resetClock(c);
        alertInitial(c);
    }

    /** Escalation is always relative to the current assignee's 4th reminder, so every new assignment resets it. */
    public void resetClock(Complaint c) {
        c.setReminderCount(0);
        c.setLastReminderAt(null);
        c.setAdminEscalatedAt(null);
        c.setUrgencyAssignedAt(LocalDateTime.now(clock));
    }

    private void alertInitial(Complaint c) {
        User official = c.getAssignedOfficial();
        if (official != null) {
            // The URGENT-worded in-app assignment notification is sent by the caller; add the email only.
            deliver(c, UrgentAlertRequested.Kind.INITIAL, List.of(official), 0, 0, null);
        } else {
            deliver(c, UrgentAlertRequested.Kind.INITIAL, activeAdmins(), 0, 0,
                    "URGENT complaint " + c.getComplaintNumber() + " is not assigned to any official. "
                            + "Please assign it so it can be verified.");
        }
    }

    public void sendReminder(Complaint c, int number, long hoursPending) {
        int total = properties.getReminderCount();
        User official = c.getAssignedOfficial();
        if (official != null) {
            deliver(c, UrgentAlertRequested.Kind.REMINDER, List.of(official), number, hoursPending,
                    "Reminder " + number + " of " + total + ": urgent complaint " + c.getComplaintNumber()
                            + " has been waiting " + hoursPending + " hours for your decision. Open it, call the citizen, "
                            + "then confirm it as urgent or mark it as normal.", total);
        } else {
            deliver(c, UrgentAlertRequested.Kind.REMINDER, activeAdmins(), number, hoursPending,
                    "Reminder " + number + " of " + total + ": urgent complaint " + c.getComplaintNumber()
                            + " is still not assigned after " + hoursPending + " hours. Please assign it to an official.", total);
        }
    }

    public void sendEscalation(Complaint c, long hoursPending) {
        int total = properties.getReminderCount();
        String who = c.getAssignedOfficial() != null ? c.getAssignedOfficial().getName() : "The assigned official";
        deliver(c, UrgentAlertRequested.Kind.ESCALATION, activeAdmins(), total, hoursPending,
                "Escalation: " + who + " was reminded " + total + " times over " + hoursPending
                        + " hours with no decision on urgent complaint " + c.getComplaintNumber()
                        + ". Please decide it or reassign it.", total);
    }

    private void deliver(Complaint c, UrgentAlertRequested.Kind kind, List<User> recipients, int number,
                         long hours, String inAppText) {
        deliver(c, kind, recipients, number, hours, inAppText, properties.getReminderCount());
    }

    private void deliver(Complaint c, UrgentAlertRequested.Kind kind, List<User> recipients, int number,
                         long hours, String inAppText, int total) {
        List<Long> emailRecipients = new ArrayList<>();
        for (User u : recipients) {
            // Only a set AND verified alert email is used (no fallback to the login email). A recipient without
            // one gets the in-app notification instead.
            boolean viaEmail = AlertEmailService.verifiedAlertEmail(u) != null;
            if (viaEmail) emailRecipients.add(u.getId());
            if (!viaEmail && inAppText != null) {
                notificationService.notify(u, inAppText, c.getId());
            }
        }
        if (!emailRecipients.isEmpty()) {
            events.publishEvent(new UrgentAlertRequested(c.getId(), kind, emailRecipients, number, total, hours));
        }
    }

    private List<User> activeAdmins() {
        return userRepository.findByRole(Role.ADMIN).stream().filter(User::isActive).toList();
    }
}
