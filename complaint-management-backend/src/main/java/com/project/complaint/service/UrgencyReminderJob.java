package com.project.complaint.service;

import com.project.complaint.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs every 60 seconds. The scan is one indexed query on urgency_status = 'PENDING', so it stays cheap;
 * each complaint is handled in its own transaction and at most one reminder per complaint is sent per run.
 * Set app.urgency.scheduler-enabled=false to switch it off (e.g. on a second backend instance).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.urgency.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class UrgencyReminderJob {

    private final ComplaintRepository complaintRepository;
    private final UrgencyReminderProcessor processor;

    @Scheduled(fixedDelayString = "${app.urgency.scheduler-interval-ms:60000}",
            initialDelayString = "${app.urgency.scheduler-initial-delay-ms:30000}")
    public void run() {
        try {
            List<Long> ids = complaintRepository.findPendingUrgentIds(UrgencyReminderProcessor.FINAL_STATUSES);
            for (Long id : ids) {
                try {
                    processor.process(id);
                } catch (Exception e) {
                    log.warn("Urgent reminder processing failed for complaint {}: {}", id, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Urgent reminder run failed: {}", e.getMessage());
        }
    }
}
