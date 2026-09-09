package com.project.complaint.service;

import com.project.complaint.dto.ComplaintDto;
import com.project.complaint.dto.PageResponse;
import com.project.complaint.entity.*;
import com.project.complaint.repository.*;
import com.project.complaint.specification.ComplaintSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    private static final Map<ComplaintStatus, Set<ComplaintStatus>> ALLOWED_TRANSITIONS = Map.of(
            ComplaintStatus.SUBMITTED, Set.of(ComplaintStatus.UNDER_REVIEW, ComplaintStatus.REJECTED),
            ComplaintStatus.UNDER_REVIEW, Set.of(ComplaintStatus.ASSIGNED, ComplaintStatus.REJECTED),
            ComplaintStatus.ASSIGNED, Set.of(ComplaintStatus.IN_PROGRESS),
            ComplaintStatus.IN_PROGRESS, Set.of(ComplaintStatus.RESOLVED),
            ComplaintStatus.RESOLVED, Set.of(ComplaintStatus.CLOSED),
            ComplaintStatus.CLOSED, Set.of(),
            ComplaintStatus.REJECTED, Set.of()
    );

    @Transactional
    public ComplaintDto.Response createComplaint(String email, ComplaintDto.CreateRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Complaint complaint = Complaint.builder()
                .user(user)
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .location(request.getLocation())
                .status(ComplaintStatus.SUBMITTED)
                .priority(Priority.MEDIUM)
                .build();

        complaint = complaintRepository.save(complaint);
        complaint.setComplaintNumber(generateComplaintNumber(complaint.getId()));
        complaint = complaintRepository.save(complaint);

        historyRepository.save(ComplaintHistory.builder()
                .complaint(complaint)
                .status(ComplaintStatus.SUBMITTED)
                .remarks("Complaint submitted by citizen")
                .updatedBy(user.getName())
                .build());

        auditLogService.log(user, "COMPLAINT_SUBMITTED", "Complaint", complaint.getId(),
                "Citizen submitted complaint " + complaint.getComplaintNumber());

        return mapToResponse(complaint);
    }

    public PageResponse<ComplaintDto.Response> getComplaints(String email, String role, int page, int limit,
            ComplaintStatus status, String category, Priority priority, String search) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long userId = null;
        Long departmentId = null;

        if (role.equals("CITIZEN")) {
            userId = user.getId();
        } else if (role.equals("OFFICIAL")) {
            if (user.getDepartment() == null) throw new RuntimeException("Official has no department assigned");
            departmentId = user.getDepartment().getId();
        }

        Specification<Complaint> spec = ComplaintSpecifications.build(userId, departmentId, null, status, category, priority, search);
        int safePage = Math.max(page - 1, 0);
        int safeLimit = limit < 1 ? 10 : Math.min(limit, 100);
        Pageable pageable = PageRequest.of(safePage, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Complaint> result = complaintRepository.findAll(spec, pageable);

        return PageResponse.<ComplaintDto.Response>builder()
                .data(result.getContent().stream().map(this::mapToResponse).collect(Collectors.toList()))
                .pagination(PageResponse.PaginationMeta.builder()
                        .page(page)
                        .limit(safeLimit)
                        .total(result.getTotalElements())
                        .totalPages(result.getTotalPages())
                        .build())
                .build();
    }

    public ComplaintDto.Response getComplaintById(Long id, String email, String role) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        checkAccess(complaint, user, role);
        return mapToResponse(complaint);
    }

    public ComplaintDto.TrackResponse trackByNumber(String complaintNumber) {
        Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber)
                .orElseThrow(() -> new RuntimeException("No complaint found with that reference number"));

        List<ComplaintDto.HistoryResponse> history = complaint.getHistory().stream()
                .map(this::mapHistory)
                .collect(Collectors.toList());

        return ComplaintDto.TrackResponse.builder()
                .complaintNumber(complaint.getComplaintNumber())
                .title(complaint.getTitle())
                .category(complaint.getCategory())
                .departmentName(complaint.getDepartment() != null ? complaint.getDepartment().getName() : null)
                .status(complaint.getStatus().name())
                .priority(complaint.getPriority().name())
                .submittedAt(complaint.getCreatedAt())
                .updatedAt(complaint.getUpdatedAt())
                .history(history)
                .build();
    }

    @Transactional
    public ComplaintDto.Response updateComplaintStatus(Long id, String email, String role,
                                                        ComplaintDto.StatusUpdateRequest request) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        User updater = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (role.equals("OFFICIAL")) {
            if (complaint.getDepartment() == null || updater.getDepartment() == null
                    || !complaint.getDepartment().getId().equals(updater.getDepartment().getId())) {
                throw new RuntimeException("You are not authorized to update this complaint");
            }
        }

        ComplaintStatus current = complaint.getStatus();
        ComplaintStatus next = request.getStatus();
        Set<ComplaintStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(next)) {
            throw new RuntimeException("Cannot change status from " + current + " to " + next);
        }

        complaint.setStatus(next);
        if (request.getResolutionInfo() != null && !request.getResolutionInfo().isBlank()) {
            complaint.setResolutionInfo(request.getResolutionInfo());
        }
        if (next == ComplaintStatus.RESOLVED) complaint.setResolvedAt(LocalDateTime.now());
        if (next == ComplaintStatus.CLOSED) complaint.setClosedAt(LocalDateTime.now());

        complaint = complaintRepository.save(complaint);

        historyRepository.save(ComplaintHistory.builder()
                .complaint(complaint)
                .status(next)
                .remarks(request.getRemarks())
                .updatedBy(updater.getName())
                .build());

        notificationService.notify(complaint.getUser(),
                "Your complaint " + complaint.getComplaintNumber() + " status changed to " + next.name().replace("_", " ") + ".",
                complaint.getId());

        auditLogService.log(updater, "STATUS_UPDATED", "Complaint", complaint.getId(),
                "Status changed from " + current + " to " + next);

        return mapToResponse(complaint);
    }

    @Transactional
    public ComplaintDto.Response reopenComplaint(Long id, String email, String reason) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!complaint.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You can only reopen your own complaints");
        }
        if (complaint.getStatus() != ComplaintStatus.RESOLVED) {
            throw new RuntimeException("Only resolved complaints can be reopened");
        }

        complaint.setStatus(ComplaintStatus.IN_PROGRESS);
        complaint.setResolvedAt(null);
        complaint = complaintRepository.save(complaint);

        historyRepository.save(ComplaintHistory.builder()
                .complaint(complaint)
                .status(ComplaintStatus.IN_PROGRESS)
                .remarks("Reopened by citizen" + (reason != null && !reason.isBlank() ? ": " + reason : ""))
                .updatedBy(user.getName())
                .build());

        if (complaint.getAssignedOfficial() != null) {
            notificationService.notify(complaint.getAssignedOfficial(),
                    "Complaint " + complaint.getComplaintNumber() + " was reopened by the citizen.",
                    complaint.getId());
        }

        return mapToResponse(complaint);
    }

    @Transactional
    public ComplaintDto.Response closeComplaint(Long id, String email) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!complaint.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You can only close your own complaints");
        }
        if (complaint.getStatus() != ComplaintStatus.RESOLVED) {
            throw new RuntimeException("Only resolved complaints can be closed");
        }

        complaint.setStatus(ComplaintStatus.CLOSED);
        complaint.setClosedAt(LocalDateTime.now());
        complaint = complaintRepository.save(complaint);

        historyRepository.save(ComplaintHistory.builder()
                .complaint(complaint)
                .status(ComplaintStatus.CLOSED)
                .remarks("Closed by citizen")
                .updatedBy(user.getName())
                .build());

        return mapToResponse(complaint);
    }

    @Transactional
    public ComplaintDto.Response assignComplaint(Long id, ComplaintDto.AssignRequest request, String adminEmail) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new RuntimeException("Department not found"));

        User official = null;
        if (request.getOfficialId() != null) {
            official = userRepository.findById(request.getOfficialId())
                    .orElseThrow(() -> new RuntimeException("Official not found"));
            if (official.getRole() != Role.OFFICIAL) {
                throw new RuntimeException("Selected user is not a government official");
            }
            if (official.getDepartment() == null || !official.getDepartment().getId().equals(department.getId())) {
                throw new RuntimeException("Selected official does not belong to the chosen department");
            }
        }

        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        complaint.setDepartment(department);
        complaint.setAssignedOfficial(official);
        if (complaint.getStatus() == ComplaintStatus.SUBMITTED || complaint.getStatus() == ComplaintStatus.UNDER_REVIEW) {
            complaint.setStatus(ComplaintStatus.ASSIGNED);
        }
        complaint = complaintRepository.save(complaint);

        String remarks = "Assigned to " + department.getName() + (official != null ? " (" + official.getName() + ")" : "");
        historyRepository.save(ComplaintHistory.builder()
                .complaint(complaint)
                .status(complaint.getStatus())
                .remarks(remarks)
                .updatedBy(admin.getName())
                .build());

        notificationService.notify(complaint.getUser(),
                "Your complaint " + complaint.getComplaintNumber() + " has been assigned to " + department.getName() + ".",
                complaint.getId());
        if (official != null) {
            notificationService.notify(official,
                    "Complaint " + complaint.getComplaintNumber() + " has been assigned to you.",
                    complaint.getId());
        }

        auditLogService.log(admin, "COMPLAINT_ASSIGNED", "Complaint", complaint.getId(), remarks);

        return mapToResponse(complaint);
    }

    @Transactional
    public ComplaintDto.Response updatePriority(Long id, String email, String role, Priority priority) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (role.equals("OFFICIAL")) {
            if (complaint.getDepartment() == null || user.getDepartment() == null
                    || !complaint.getDepartment().getId().equals(user.getDepartment().getId())) {
                throw new RuntimeException("You are not authorized to update this complaint");
            }
        }

        Priority old = complaint.getPriority();
        complaint.setPriority(priority);
        complaint = complaintRepository.save(complaint);

        auditLogService.log(user, "PRIORITY_CHANGED", "Complaint", complaint.getId(),
                "Priority changed from " + old + " to " + priority);

        return mapToResponse(complaint);
    }

    public List<ComplaintDto.HistoryResponse> getComplaintHistory(Long complaintId) {
        return historyRepository.findByComplaintIdOrderByUpdatedAtDesc(complaintId)
                .stream().map(this::mapHistory).collect(Collectors.toList());
    }

    public Map<String, Object> getCitizenStats(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<Complaint> mine = complaintRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", mine.size());
        stats.put("pending", mine.stream().filter(c -> c.getStatus() == ComplaintStatus.SUBMITTED || c.getStatus() == ComplaintStatus.UNDER_REVIEW).count());
        stats.put("inProgress", mine.stream().filter(c -> c.getStatus() == ComplaintStatus.ASSIGNED || c.getStatus() == ComplaintStatus.IN_PROGRESS).count());
        stats.put("resolved", mine.stream().filter(c -> c.getStatus() == ComplaintStatus.RESOLVED).count());
        stats.put("closed", mine.stream().filter(c -> c.getStatus() == ComplaintStatus.CLOSED).count());
        return stats;
    }

    public Map<String, Object> getOfficialStats(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getDepartment() == null) throw new RuntimeException("Official has no department assigned");
        List<Complaint> dept = complaintRepository.findByDepartmentIdOrderByCreatedAtDesc(user.getDepartment().getId());
        LocalDateTime overdueThreshold = LocalDateTime.now().minusDays(5);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("assigned", dept.size());
        stats.put("pendingReview", dept.stream().filter(c -> c.getStatus() == ComplaintStatus.ASSIGNED).count());
        stats.put("inProgress", dept.stream().filter(c -> c.getStatus() == ComplaintStatus.IN_PROGRESS).count());
        stats.put("resolved", dept.stream().filter(c -> c.getStatus() == ComplaintStatus.RESOLVED).count());
        stats.put("overdue", dept.stream().filter(c ->
                (c.getStatus() == ComplaintStatus.ASSIGNED || c.getStatus() == ComplaintStatus.IN_PROGRESS)
                        && c.getUpdatedAt() != null && c.getUpdatedAt().isBefore(overdueThreshold)).count());
        return stats;
    }

    public long countAll() { return complaintRepository.count(); }
    public long countByStatus(ComplaintStatus status) { return complaintRepository.countByStatus(status); }

    public Map<String, Long> countByCategory() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Complaint c : complaintRepository.findAll()) {
            result.merge(c.getCategory(), 1L, Long::sum);
        }
        return result;
    }

    public Map<String, Long> countByDepartmentName() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Complaint c : complaintRepository.findAll()) {
            if (c.getDepartment() != null) {
                result.merge(c.getDepartment().getName(), 1L, Long::sum);
            }
        }
        return result;
    }

    public Map<String, Long> monthlyVolume() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yyyy");
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 5; i >= 0; i--) {
            result.put(now.minusMonths(i).format(fmt), 0L);
        }
        for (Complaint c : complaintRepository.findAll()) {
            if (c.getCreatedAt() == null) continue;
            String key = c.getCreatedAt().format(fmt);
            if (result.containsKey(key)) {
                result.merge(key, 1L, Long::sum);
            }
        }
        return result;
    }

    private void checkAccess(Complaint complaint, User user, String role) {
        if (role.equals("CITIZEN") && !complaint.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You do not have access to this complaint");
        }
        if (role.equals("OFFICIAL")) {
            if (complaint.getDepartment() == null || user.getDepartment() == null
                    || !complaint.getDepartment().getId().equals(user.getDepartment().getId())) {
                throw new RuntimeException("You do not have access to this complaint");
            }
        }
    }

    private String generateComplaintNumber(Long id) {
        int year = LocalDateTime.now().getYear();
        return String.format("CMP-%d-%06d", year, id);
    }

    private ComplaintDto.HistoryResponse mapHistory(ComplaintHistory h) {
        return ComplaintDto.HistoryResponse.builder()
                .id(h.getId())
                .status(h.getStatus().name())
                .remarks(h.getRemarks())
                .updatedBy(h.getUpdatedBy())
                .updatedAt(h.getUpdatedAt())
                .build();
    }

    private ComplaintDto.Response mapToResponse(Complaint c) {
        List<ComplaintDto.HistoryResponse> history = c.getHistory() != null
                ? c.getHistory().stream().map(this::mapHistory).collect(Collectors.toList())
                : List.of();

        return ComplaintDto.Response.builder()
                .id(c.getId())
                .complaintNumber(c.getComplaintNumber())
                .title(c.getTitle())
                .description(c.getDescription())
                .category(c.getCategory())
                .location(c.getLocation())
                .priority(c.getPriority() != null ? c.getPriority().name() : Priority.MEDIUM.name())
                .status(c.getStatus().name())
                .resolutionInfo(c.getResolutionInfo())
                .citizenName(c.getUser().getName())
                .citizenEmail(c.getUser().getEmail())
                .departmentId(c.getDepartment() != null ? c.getDepartment().getId() : null)
                .departmentName(c.getDepartment() != null ? c.getDepartment().getName() : null)
                .assignedOfficialId(c.getAssignedOfficial() != null ? c.getAssignedOfficial().getId() : null)
                .assignedOfficialName(c.getAssignedOfficial() != null ? c.getAssignedOfficial().getName() : null)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .resolvedAt(c.getResolvedAt())
                .closedAt(c.getClosedAt())
                .history(history)
                .build();
    }
}
