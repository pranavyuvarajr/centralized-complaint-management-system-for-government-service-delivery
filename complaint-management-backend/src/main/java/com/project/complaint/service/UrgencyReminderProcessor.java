package com.project.complaint.service;

import com.project.complaint.config.UrgencyProperties;
import com.project.complaint.entity.Complaint;
import com.project.complaint.entity.ComplaintStatus;
import com.project.complaint.entity.UrgencyStatus;
import com.project.complaint.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Processes ONE pending urgent complaint per call: decides whether a reminder or the admin escalation
 * is due, claims it with a conditional UPDATE (so two runs or two instances can never both send it),
 * then alerts. Reminders are intentionally not written to the citizen-visible timeline; they are logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrgencyReminderProcessor {

    static final List<ComplaintStatus> FINAL_STATUSES =
            List.of(ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED, ComplaintStatus.REJECTED);

    public enum Type { REMINDER, ESCALATION }

    public record Due(Type type, int number) {}

    private final ComplaintRepository complaintRepository;
    private final UrgentAlertService alerts;
    private final UrgencyProperties properties;
    private final Clock clock;

    /**
     * Pure schedule logic. Reminder n is due at anchor + n * interval, but never sooner than half an
     * interval after the previous reminder, so after downtime the catch-up reminders are spread out
     * instead of arriving in a burst. The escalation (only for an assigned complaint) is due
     * escalationDelay after the last reminder and is sent once.
     */
    public static Optional<Due> nextDue(Complaint c, LocalDateTime now, UrgencyProperties p) {
        if (c.getUrgencyStatus() != UrgencyStatus.PENDING || FINAL_STATUSES.contains(c.getStatus())) {
            return Optional.empty();
        }
        LocalDateTime anchor = c.getUrgencyAssignedAt() != null ? c.getUrgencyAssignedAt() : c.getCreatedAt();
        if (anchor == null) return Optional.empty();
        int count = c.getReminderCount();
        Duration interval = p.getReminderInterval();

        if (count < p.getReminderCount()) {
            LocalDateTime due = anchor.plus(interval.multipliedBy(count + 1L));
            if (c.getLastReminderAt() != null) {
                LocalDateTime spaced = c.getLastReminderAt().plus(interval.dividedBy(2));
                if (spaced.isAfter(due)) due = spaced;
            }
            return now.isBefore(due) ? Optional.empty() : Optional.of(new Due(Type.REMINDER, count + 1));
        }
        if (c.getAssignedOfficial() != null && c.getAdminEscalatedAt() == null) {
            LocalDateTime base = c.getLastReminderAt() != null ? c.getLastReminderAt()
                    : anchor.plus(interval.multipliedBy(count));
            return now.isBefore(base.plus(p.getEscalationDelay())) ? Optional.empty() : Optional.of(new Due(Type.ESCALATION, count));
        }
        return Optional.empty();
    }

    /** @return true if a reminder or escalation was claimed and sent by this call */
    @Transactional
    public boolean process(Long complaintId) {
        Complaint c = complaintRepository.findById(complaintId).orElse(null);
        if (c == null) return false;
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<Due> due = nextDue(c, now, properties);
        if (due.isEmpty() || c.getUrgencyAssignedAt() == null) return false;

        LocalDateTime anchor = c.getUrgencyAssignedAt();
        int claimed = due.get().type() == Type.REMINDER
                ? complaintRepository.claimReminder(complaintId, c.getReminderCount(), due.get().number(), now, anchor, FINAL_STATUSES)
                : complaintRepository.claimEscalation(complaintId, c.getReminderCount(), now, anchor, FINAL_STATUSES);
        if (claimed == 0) return false; // already sent, decided, reset by a reassignment, or finished

        // The claim cleared the persistence context: reload before touching lazy associations.
        Complaint fresh = complaintRepository.findById(complaintId).orElse(null);
        if (fresh == null) return false;
        long hours = Duration.between(anchor, now).toHours();
        if (due.get().type() == Type.REMINDER) {
            alerts.sendReminder(fresh, due.get().number(), hours);
            log.info("Urgent complaint {}: reminder {} of {} sent ({}h pending)", fresh.getComplaintNumber(),
                    due.get().number(), properties.getReminderCount(), hours);
        } else {
            alerts.sendEscalation(fresh, hours);
            log.info("Urgent complaint {}: escalated to admins ({}h pending)", fresh.getComplaintNumber(), hours);
        }
        return true;
    }
}
