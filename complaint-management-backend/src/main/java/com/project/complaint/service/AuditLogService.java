package com.project.complaint.service;

import com.project.complaint.dto.AuditLogDto;
import com.project.complaint.entity.AuditLog;
import com.project.complaint.entity.User;
import com.project.complaint.repository.AuditLogRepository;
import com.project.complaint.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public void log(User performedBy, String action, String entityType, Long entityId, String description) {
        auditLogRepository.save(AuditLog.builder()
                .performedBy(performedBy != null ? performedBy.getName() + " (" + performedBy.getEmail() + ")" : "System")
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .build());
    }

    public void logByEmail(String email, String action, String entityType, Long entityId, String description) {
        User user = userRepository.findByEmail(email).orElse(null);
        log(user, action, entityType, entityId, description);
    }

    public Page<AuditLogDto.Response> getLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(a -> AuditLogDto.Response.builder()
                        .id(a.getId())
                        .performedBy(a.getPerformedBy())
                        .action(a.getAction())
                        .entityType(a.getEntityType())
                        .entityId(a.getEntityId())
                        .description(a.getDescription())
                        .createdAt(a.getCreatedAt())
                        .build());
    }
}
