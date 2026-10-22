package com.project.complaint.service;

import com.project.complaint.entity.Complaint;
import com.project.complaint.entity.Role;
import com.project.complaint.entity.User;
import com.project.complaint.repository.ComplaintRepository;
import com.project.complaint.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends urgent-alert emails after the originating transaction commits, off the request thread.
 * A mail failure is logged per recipient and can never affect a complaint submission or assignment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UrgentAlertMailer {

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Async("alertTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onAlert(UrgentAlertRequested event) {
        try {
            Complaint c = complaintRepository.findById(event.complaintId()).orElse(null);
            if (c == null) return;
            UrgentAlertEmailBuilder.Data data = new UrgentAlertEmailBuilder.Data(
                    c.getId(), c.getComplaintNumber(), c.getTitle(), c.getCategory(), c.getResolvedAddress(),
                    c.getCreatedAt(), c.getDescription(), c.getUrgencyReason(),
                    c.getAssignedOfficial() != null ? c.getAssignedOfficial().getName() : null,
                    event.reminderNumber(), event.reminderTotal(), event.hoursPending());

            for (Long userId : event.recipientUserIds()) {
                try {
                    User u = userRepository.findById(userId).orElse(null);
                    String address = AlertEmailService.verifiedAlertEmail(u); // never the login email
                    if (address == null) continue;
                    emailService.send(UrgentAlertEmailBuilder.build(address, u.getName(), u.getRole() == Role.ADMIN,
                            event.kind(), data, frontendBaseUrl));
                } catch (Exception e) {
                    log.warn("Urgent alert email ({}) for complaint {} to user {} failed: {}",
                            event.kind(), event.complaintId(), userId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Urgent alert ({}) for complaint {} could not be prepared: {}", event.kind(), event.complaintId(), e.getMessage());
        }
    }
}
