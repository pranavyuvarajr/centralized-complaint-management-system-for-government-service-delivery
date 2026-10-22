package com.project.complaint.service;

import com.project.complaint.dto.ComplaintDto;
import com.project.complaint.entity.*;
import com.project.complaint.repository.ComplaintHistoryRepository;
import com.project.complaint.repository.ComplaintRepository;
import com.project.complaint.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Rules for the urgent-complaint feature: submission validation, the verification decision
 * (first decision wins, then locked), the admin override, the priority lock, and what extra
 * data each caller may see. Kept out of ComplaintService on purpose; ComplaintService only calls
 * small hooks into this class.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UrgencyService {

    /** Prefix of every urgency timeline entry, so the public track page can filter them out. */
    public static final String HISTORY_PREFIX = "Urgency: ";

    public static final String DECISION_CONFIRM = "CONFIRM_URGENT";
    public static final String DECISION_MARK_NORMAL = "MARK_NORMAL";

    static final int REASON_MIN = 20;
    static final int REASON_MAX = 1000;
    static final int NOTE_MIN = 10;

    private final ComplaintRepository complaintRepository;
    private final ComplaintHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    // ------------------------------------------------------------------ submission

    /**
     * Validates an urgent submission. Returns the trimmed justification for an urgent complaint,
     * or null for a normal one (in which case any urgencyReason that was sent is ignored).
     */
    public String validateUrgentRequest(boolean urgent, String urgencyReason, User citizen) {
        if (!urgent) {
            return null;
        }
        String reason = urgencyReason == null ? "" : urgencyReason.trim();
        if (reason.length() < REASON_MIN || reason.length() > REASON_MAX) {
            throw new RuntimeException("Please explain why this complaint is urgent ("
                    + REASON_MIN + " to " + REASON_MAX + " characters).");
        }
        if (citizen.getPhone() == null || citizen.getPhone().isBlank()) {
            throw new RuntimeException("A phone number on your account is required to file an urgent complaint, "
                    + "so the official can call you to verify it.");
        }
        return reason;
    }

    // ------------------------------------------------------------------ decision

    /** Assigned official of the complaint, or any admin. Other officials cannot decide. */
    public boolean canDecide(Complaint complaint, User user, String role) {
        if ("ADMIN".equals(role)) return true;
        return "OFFICIAL".equals(role)
                && complaint.getAssignedOfficial() != null
                && complaint.getAssignedOfficial().getId().equals(user.getId());
    }

    /**
     * Records the first decision on a PENDING complaint: CONFIRM_URGENT keeps priority URGENT;
     * MARK_NORMAL (reason required) drops it to MEDIUM. Locked afterwards. Concurrent decisions
     * are serialised by a conditional UPDATE ... WHERE urgency_status = 'PENDING'.
     *
     * @return the complaint id (callers load the response through the normal single-complaint path)
     */
    @Transactional
    public Long decide(Long complaintId, String email, String role, String decision, String note) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!canDecide(complaint, user, role)) {
            throw new RuntimeException("Only the assigned official or an administrator can decide on an urgent complaint.");
        }
        if (complaint.getUrgencyStatus() == UrgencyStatus.NONE) {
            throw new RuntimeException("This complaint was not submitted as urgent.");
        }
        if (complaint.getUrgencyStatus() != UrgencyStatus.PENDING) {
            throw new RuntimeException("A decision has already been recorded for this complaint and it is locked. "
                    + "Only an administrator can change it.");
        }

        boolean confirm = parseDecision(decision);
        String cleanNote = note == null ? "" : note.trim();
        if (!confirm && cleanNote.length() < NOTE_MIN) {
            throw new RuntimeException("Please give a reason for marking this complaint as normal (at least "
                    + NOTE_MIN + " characters), for example that it is not urgent or the citizen could not be reached.");
        }

        UrgencyStatus target = confirm ? UrgencyStatus.CONFIRMED_URGENT : UrgencyStatus.DOWNGRADED_TO_NORMAL;
        Priority priority = confirm ? Priority.URGENT : Priority.MEDIUM;
        LocalDateTime now = LocalDateTime.now(clock);

        int changed = complaintRepository.decideIfPending(complaintId, target, user, now,
                cleanNote.isEmpty() ? null : cleanNote, priority);
        if (changed == 0) {
            throw new IllegalStateException("Another decision was recorded for this complaint just before yours. "
                    + "Refresh the page to see it.");
        }

        String remarks = confirm
                ? HISTORY_PREFIX + "verified as urgent" + (cleanNote.isEmpty() ? "" : ". Note: " + cleanNote)
                : HISTORY_PREFIX + "initially marked urgent, changed to normal. Reason: " + cleanNote;
        historyRepository.save(ComplaintHistory.builder()
                .complaint(complaintRepository.getReferenceById(complaintId))
                .status(complaint.getStatus())
                .remarks(remarks)
                .updatedBy(user.getName())
                .build());

        auditLogService.log(user, confirm ? "URGENCY_CONFIRMED" : "URGENCY_DOWNGRADED", "Complaint", complaintId,
                complaint.getComplaintNumber() + (confirm ? " confirmed as urgent" : " changed to normal")
                        + (cleanNote.isEmpty() ? "" : ". " + cleanNote));
        return complaintId;
    }

    /**
     * Admin-only: change an already-locked decision to either state. A note is mandatory, priority
     * follows the new state, the citizen is notified, and the original justification and the
     * "initially urgent" flag are kept. Reminder state is deliberately left untouched.
     */
    @Transactional
    public Long overrideDecision(Long complaintId, String adminEmail, String decision, String note) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (admin.getRole() != Role.ADMIN) {
            throw new RuntimeException("Only an administrator can override an urgency decision.");
        }

        UrgencyStatus current = complaint.getUrgencyStatus();
        if (current == UrgencyStatus.NONE) {
            throw new RuntimeException("This complaint was not submitted as urgent.");
        }
        if (current == UrgencyStatus.PENDING) {
            throw new RuntimeException("No decision has been made yet. Confirm it as urgent or mark it as normal instead.");
        }

        boolean confirm = parseDecision(decision);
        UrgencyStatus target = confirm ? UrgencyStatus.CONFIRMED_URGENT : UrgencyStatus.DOWNGRADED_TO_NORMAL;
        if (target == current) {
            throw new RuntimeException("The complaint is already "
                    + (confirm ? "verified as urgent." : "marked as normal."));
        }
        String cleanNote = note == null ? "" : note.trim();
        if (cleanNote.length() < NOTE_MIN) {
            throw new RuntimeException("A note explaining the override is required (at least " + NOTE_MIN + " characters).");
        }

        complaint.setUrgencyStatus(target);
        complaint.setPriority(confirm ? Priority.URGENT : Priority.MEDIUM);
        complaint.setUrgencyDecidedBy(admin);
        complaint.setUrgencyDecidedAt(LocalDateTime.now(clock));
        complaint.setUrgencyDecisionNote(cleanNote);
        complaint.setUrgencyOverridden(true);
        complaint = complaintRepository.save(complaint);

        String outcome = confirm ? "now verified as urgent" : "now changed to normal";
        historyRepository.save(ComplaintHistory.builder()
                .complaint(complaint)
                .status(complaint.getStatus())
                .remarks(HISTORY_PREFIX + "decision changed by admin " + admin.getName() + ", " + outcome
                        + ". Reason: " + cleanNote)
                .updatedBy(admin.getName())
                .build());

        notificationService.notify(complaint.getUser(),
                "An administrator updated the urgency of your complaint " + complaint.getComplaintNumber() + ": "
                        + (confirm ? "it is now verified as urgent." : "it has been changed to normal.")
                        + " Reason: " + cleanNote,
                complaint.getId());

        auditLogService.log(admin, "URGENCY_OVERRIDDEN", "Complaint", complaintId,
                complaint.getComplaintNumber() + " " + outcome + " by admin override. " + cleanNote);
        return complaintId;
    }

    private boolean parseDecision(String decision) {
        if (DECISION_CONFIRM.equalsIgnoreCase(decision)) return true;
        if (DECISION_MARK_NORMAL.equalsIgnoreCase(decision)) return false;
        throw new RuntimeException("Decision must be CONFIRM_URGENT or MARK_NORMAL.");
    }

    // ------------------------------------------------------------------ priority lock

    /**
     * Called by ComplaintService.updatePriority, which serves both the official and the admin
     * priority endpoints. Urgent-flow complaints are locked (only the admin override changes
     * priority); for normal complaints URGENT can no longer be set by hand.
     */
    public void assertPriorityChangeAllowed(Complaint complaint, Priority requested) {
        UrgencyStatus status = complaint.getUrgencyStatus() == null ? UrgencyStatus.NONE : complaint.getUrgencyStatus();
        if (status != UrgencyStatus.NONE) {
            throw new RuntimeException("The priority of an urgent-flow complaint is controlled by its urgency "
                    + "verification and cannot be changed manually. An administrator can change the urgency decision instead.");
        }
        if (requested == Priority.URGENT) {
            throw new RuntimeException("Urgent priority can only come from an urgent complaint filed by the citizen "
                    + "(or an administrator's urgency override) and cannot be set manually.");
        }
    }

    // ------------------------------------------------------------------ what the caller sees

    /** Public track page: a badge label only. Nothing urgent is shown after a downgrade. */
    public String trackLabel(Complaint complaint) {
        UrgencyStatus status = complaint.getUrgencyStatus();
        if (status == UrgencyStatus.PENDING) return "Urgent (pending verification)";
        if (status == UrgencyStatus.CONFIRMED_URGENT) return "Urgent (verified)";
        return null;
    }

    /** True for timeline entries written by this feature (hidden from the public track page). */
    public static boolean isUrgencyHistory(String remarks) {
        return remarks != null && remarks.startsWith(HISTORY_PREFIX);
    }

    /**
     * Fills the caller-specific fields of a single-complaint response. The citizen's phone goes
     * ONLY to admins and the assigned official, and only for urgent-flow complaints, so normal
     * complaints behave exactly as before.
     */
    public void decorateForCaller(ComplaintDto.Response response, Complaint complaint, User caller, String role) {
        UrgencyStatus status = complaint.getUrgencyStatus() == null ? UrgencyStatus.NONE : complaint.getUrgencyStatus();
        if (status == UrgencyStatus.NONE) {
            response.setCanDecideUrgency(false);
            response.setCanOverrideUrgency(false);
            return;
        }
        boolean decider = canDecide(complaint, caller, role);
        response.setCanDecideUrgency(status == UrgencyStatus.PENDING && decider);
        response.setCanOverrideUrgency("ADMIN".equals(role) && status != UrgencyStatus.PENDING);
        if (decider) {
            response.setCitizenPhone(complaint.getUser().getPhone());
        }
    }
}
