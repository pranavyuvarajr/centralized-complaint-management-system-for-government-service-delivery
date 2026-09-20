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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComplaintService {

    private static final Set<ComplaintStatus> FINAL_STATUSES =
            Set.of(ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED, ComplaintStatus.REJECTED);

    private final ComplaintRepository complaintRepository;
    private final ComplaintHistoryRepository historyRepository;
    private final ComplaintCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final AttachmentService attachmentService;
    private final GeoLocationService geoLocationService;
    private final ComplaintCommentRepository commentRepository;
    private final FeedbackRepository feedbackRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;

    private static final Map<ComplaintStatus, Set<ComplaintStatus>> ALLOWED_TRANSITIONS = Map.of(
            ComplaintStatus.SUBMITTED, Set.of(ComplaintStatus.UNDER_REVIEW, ComplaintStatus.REJECTED),
            ComplaintStatus.UNDER_REVIEW, Set.of(ComplaintStatus.ASSIGNED, ComplaintStatus.REJECTED),
            ComplaintStatus.ASSIGNED, Set.of(ComplaintStatus.IN_PROGRESS),
            ComplaintStatus.IN_PROGRESS, Set.of(ComplaintStatus.RESOLVED),
            ComplaintStatus.RESOLVED, Set.of(ComplaintStatus.CLOSED),
            ComplaintStatus.CLOSED, Set.of(),
            ComplaintStatus.REJECTED, Set.of()
    );

    /**
     * Creates a complaint. Photo and location are independent of each other:
     * <ul>
     *   <li>A photo is only mandatory when the chosen category was configured by
     *   an admin to require one (see {@link ComplaintCategory#isImageRequired()}).</li>
     *   <li>A location (coordinates from the map pin, address search or the
     *   device's current location) is only mandatory when the category requires
     *   it (see {@link ComplaintCategory#isLocationRequired()}). When it is
     *   optional and none is sent, the complaint is saved without one. When
     *   coordinates are sent they are always validated and used, whether or not
     *   the category requires them. The photo's own GPS tags are never used.</li>
     * </ul>
     * <p>
     * If the chosen category has an auto-routing department configured, the
     * complaint is immediately routed to that department and handed to
     * whichever active official there currently has the fewest ongoing
     * complaints — admin can still reassign it manually at any time
     * afterward, same as any other complaint.
     */
    @Transactional
    public ComplaintDto.Response createComplaint(String email, ComplaintDto.CreateRequest request, MultipartFile photo) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        validateBasics(request);

        Optional<ComplaintCategory> categoryEntity = categoryRepository.findByName(request.getCategory());
        // Categories created before this setting existed, or an unrecognized
        // category name, default to requiring a photo - the previous behavior.
        boolean imageRequired = categoryEntity.map(ComplaintCategory::isImageRequired).orElse(true);
        boolean locationRequired = categoryEntity.map(ComplaintCategory::isLocationRequired).orElse(true);

        boolean hasPhoto = photo != null && !photo.isEmpty();
        if (imageRequired && !hasPhoto) {
            throw new RuntimeException("A photo of the issue is required for the \"" + request.getCategory() + "\" category.");
        }
        if (hasPhoto) {
            if (photo.getSize() > 5L * 1024 * 1024) {
                throw new RuntimeException("Photo exceeds the 5MB size limit");
            }
            if (photo.getContentType() == null || !(photo.getContentType().equals("image/jpeg")
                    || photo.getContentType().equals("image/png"))) {
                throw new RuntimeException("Only JPG or PNG photos are allowed");
            }
        }

        // Location comes only from what the citizen marked (map pin, search or
        // current location) — never from the photo.
        Double latitude = null;
        Double longitude = null;
        String resolvedAddress = null;
        boolean locationSent = request.getLatitude() != null || request.getLongitude() != null;
        if (locationSent) {
            if (!geoLocationService.isValidCoordinate(request.getLatitude(), request.getLongitude())) {
                throw new RuntimeException(
                        "The selected location is not valid. Search for an address, use your current location, "
                                + "or drop a pin on the map again.");
            }
            latitude = request.getLatitude();
            longitude = request.getLongitude();
            resolvedAddress = geoLocationService.reverseGeocode(latitude, longitude);
        } else if (locationRequired) {
            throw new RuntimeException(
                    "A location is required for the \"" + request.getCategory() + "\" category. Search for an address, "
                            + "use your current location, or drop a pin on the map to mark where the issue is.");
        }

        Complaint complaint = Complaint.builder()
                .user(user)
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .category(request.getCategory())
                .latitude(latitude)
                .longitude(longitude)
                .resolvedAddress(resolvedAddress)
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
                "Citizen submitted complaint " + complaint.getComplaintNumber()
                        + (resolvedAddress != null ? " at \"" + resolvedAddress + "\"" : " (no location provided)")
                        + (hasPhoto ? " with a photo attached" : " (no photo attached)"));

        // Attach the evidence photo, if one was provided. Runs inside the same
        // transaction, so if this fails the whole complaint creation rolls back.
        if (hasPhoto) {
            attachmentService.upload(complaint.getId(), email, "CITIZEN", photo);
        }

        complaint = autoRouteComplaint(complaint, user);

        return mapToResponse(complaint);
    }

    private void validateBasics(ComplaintDto.CreateRequest request) {
        String title = request.getTitle() == null ? "" : request.getTitle().trim();
        if (title.length() < 5 || title.length() > 150) {
            throw new RuntimeException("Title must be between 5 and 150 characters");
        }
        String description = request.getDescription() == null ? "" : request.getDescription().trim();
        if (description.length() < 20) {
            throw new RuntimeException("Description must be at least 20 characters");
        }
        if (request.getCategory() == null || request.getCategory().isBlank()) {
            throw new RuntimeException("Category is required");
        }
    }

    /**
     * Auto-assigns a freshly submitted complaint to its category's linked
     * department and least-busy active official, if one is configured and
     * available. Silently leaves the complaint unassigned (same as before)
     * when the category has no department mapping or no eligible official —
     * nothing here is a hard requirement.
     */
    private Complaint autoRouteComplaint(Complaint complaint, User citizen) {
        Optional<ComplaintCategory> category = categoryRepository.findByName(complaint.getCategory());
        if (category.isEmpty() || category.get().getDepartment() == null) {
            return complaint;
        }
        Department department = category.get().getDepartment();
        if (!department.isActive()) {
            return complaint;
        }

        List<User> officials = userRepository.findByDepartmentId(department.getId()).stream()
                .filter(u -> u.getRole() == Role.OFFICIAL && u.isActive())
                .collect(Collectors.toList());
        if (officials.isEmpty()) {
            return complaint;
        }

        User leastBusy = officials.stream()
                .min(Comparator
                        .comparingLong((User o) -> complaintRepository
                                .countByAssignedOfficialIdAndStatusNotIn(o.getId(), List.copyOf(FINAL_STATUSES)))
                        .thenComparing(User::getId))
                .orElseThrow();

        complaint.setDepartment(department);
        complaint.setAssignedOfficial(leastBusy);
        complaint.setStatus(ComplaintStatus.ASSIGNED);
        complaint = complaintRepository.save(complaint);

        String remarks = "Auto-assigned to " + department.getName() + " (" + leastBusy.getName()
                + ") based on category \"" + complaint.getCategory() + "\"";
        historyRepository.save(ComplaintHistory.builder()
                .complaint(complaint)
                .status(ComplaintStatus.ASSIGNED)
                .remarks(remarks)
                .updatedBy("System (auto-routing)")
                .build());

        notificationService.notify(citizen,
                "Your complaint " + complaint.getComplaintNumber() + " has been automatically routed to "
                        + department.getName() + ".",
                complaint.getId());
        notificationService.notify(leastBusy,
                "Complaint " + complaint.getComplaintNumber() + " has been auto-assigned to you.",
                complaint.getId());

        auditLogService.log(citizen, "COMPLAINT_AUTO_ASSIGNED", "Complaint", complaint.getId(), remarks);

        return complaint;
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
        return withCompletionFlag(mapToResponse(complaint), complaint);
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

    /**
     * Staff (official of the complaint's department, or admin) moves a
     * complaint along its status chain. Two rules matter here:
     * <ul>
     *   <li>Resolving a complaint whose category requires a photo must go through
     *   {@link #resolveComplaint}, which takes the completion photo in the same
     *   request; it is refused here so the photo rule can't be bypassed.</li>
     *   <li>Only the citizen who filed a complaint can close it (from their own
     *   complaint page), so nobody on staff can end the citizen's chance to reopen it.</li>
     * </ul>
     */
    @Transactional
    public ComplaintDto.Response updateComplaintStatus(Long id, String email, String role,
                                                        ComplaintDto.StatusUpdateRequest request) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        User updater = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        requireStaffAccess(complaint, updater, role);

        ComplaintStatus next = request.getStatus();
        requireAllowedTransition(complaint.getStatus(), next);

        if (next == ComplaintStatus.CLOSED) {
            throw new RuntimeException("Only the citizen who filed this complaint can close it, from their own complaint page.");
        }
        if (next == ComplaintStatus.RESOLVED && completionPhotoRequired(complaint)) {
            throw new RuntimeException("The \"" + complaint.getCategory() + "\" category requires a completion photo. "
                    + "Use the Resolve action and upload a photo of the finished work.");
        }

        complaint = applyStatusChange(complaint, updater, next, request.getRemarks(), request.getResolutionInfo(), false);
        return withCompletionFlag(mapToResponse(complaint), complaint);
    }

    /**
     * Marks a complaint RESOLVED together with a completion photo, in one
     * transaction. The photo is required whenever the category requires a photo
     * from the citizen (the same setting), for officials and admins alike; when
     * the category doesn't require one it is optional. Everything except the
     * photo (transition check, history, notification, audit) is the shared
     * status-change logic, so both routes behave identically.
     */
    @Transactional
    public ComplaintDto.Response resolveComplaint(Long id, String email, String role,
                                                   String remarks, String resolutionInfo, MultipartFile photo) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        User updater = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        requireStaffAccess(complaint, updater, role);
        requireAllowedTransition(complaint.getStatus(), ComplaintStatus.RESOLVED);

        boolean hasPhoto = photo != null && !photo.isEmpty();
        if (!hasPhoto && completionPhotoRequired(complaint)) {
            throw new RuntimeException("A completion photo is required for the \"" + complaint.getCategory()
                    + "\" category before this complaint can be marked resolved.");
        }
        if (hasPhoto) {
            attachmentService.validatePhoto(photo); // reject a bad file before changing anything
        }

        complaint = applyStatusChange(complaint, updater, ComplaintStatus.RESOLVED, remarks, resolutionInfo, hasPhoto);
        if (hasPhoto) {
            attachmentService.addPhoto(complaint, updater, AttachmentKind.COMPLETION, photo);
        }
        return withCompletionFlag(mapToResponse(complaint), complaint);
    }

    /** Only admins, or officials of the complaint's own department, may change a complaint's status. */
    private void requireStaffAccess(Complaint complaint, User updater, String role) {
        if (!"OFFICIAL".equals(role) && !"ADMIN".equals(role)) {
            throw new RuntimeException("You are not authorized to update this complaint");
        }
        if ("OFFICIAL".equals(role)) {
            if (complaint.getDepartment() == null || updater.getDepartment() == null
                    || !complaint.getDepartment().getId().equals(updater.getDepartment().getId())) {
                throw new RuntimeException("You are not authorized to update this complaint");
            }
        }
    }

    private void requireAllowedTransition(ComplaintStatus current, ComplaintStatus next) {
        Set<ComplaintStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (next == null || !allowed.contains(next)) {
            throw new RuntimeException("Cannot change status from " + current + " to " + next);
        }
    }

    /** The one place a status change is applied: status, dates, history, citizen notification, audit log. */
    private Complaint applyStatusChange(Complaint complaint, User updater, ComplaintStatus next,
                                        String remarks, String resolutionInfo, boolean photoAttached) {
        ComplaintStatus current = complaint.getStatus();

        complaint.setStatus(next);
        if (resolutionInfo != null && !resolutionInfo.isBlank()) {
            complaint.setResolutionInfo(resolutionInfo);
        }
        if (next == ComplaintStatus.RESOLVED) complaint.setResolvedAt(LocalDateTime.now());
        if (next == ComplaintStatus.CLOSED) complaint.setClosedAt(LocalDateTime.now());

        complaint = complaintRepository.save(complaint);

        String historyRemarks = remarks;
        if (photoAttached) {
            historyRemarks = (remarks == null || remarks.isBlank())
                    ? "Completion photo attached"
                    : remarks + " (completion photo attached)";
        }
        historyRepository.save(ComplaintHistory.builder()
                .complaint(complaint)
                .status(next)
                .remarks(historyRemarks)
                .updatedBy(updater.getName())
                .build());

        notificationService.notify(complaint.getUser(),
                "Your complaint " + complaint.getComplaintNumber() + " status changed to " + next.name().replace("_", " ") + ".",
                complaint.getId());

        auditLogService.log(updater, "STATUS_UPDATED", "Complaint", complaint.getId(),
                "Status changed from " + current + " to " + next + (photoAttached ? " with a completion photo" : ""));

        return complaint;
    }

    /**
     * Whether resolving this complaint needs a completion photo: the same
     * setting as the citizen's photo (unknown category -> required, matching
     * how filing treats it).
     */
    private boolean completionPhotoRequired(Complaint complaint) {
        return categoryRepository.findByName(complaint.getCategory())
                .map(ComplaintCategory::isImageRequired)
                .orElse(true);
    }

    private ComplaintDto.Response withCompletionFlag(ComplaintDto.Response response, Complaint complaint) {
        response.setCompletionPhotoRequired(completionPhotoRequired(complaint));
        return response;
    }

    @Transactional
    public ComplaintDto.Response reopenComplaint(Long id, String email, String reason) {
        return reopenComplaint(id, email, reason, null);
    }

    /**
     * The citizen reopens their own RESOLVED complaint. A reason is required so
     * the department knows what's still wrong, and a photo can be added to show
     * it. Earlier photos (including the completion photo) are kept; resolving
     * again requires a fresh completion photo because resolving always goes
     * through {@link #resolveComplaint}.
     */
    @Transactional
    public ComplaintDto.Response reopenComplaint(Long id, String email, String reason, MultipartFile photo) {
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
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.length() < 10) {
            throw new RuntimeException("Please explain why you are reopening this complaint (at least 10 characters).");
        }
        boolean hasPhoto = photo != null && !photo.isEmpty();
        if (hasPhoto) {
            attachmentService.validatePhoto(photo);
        }

        complaint.setStatus(ComplaintStatus.IN_PROGRESS);
        complaint.setResolvedAt(null);
        complaint = complaintRepository.save(complaint);

        historyRepository.save(ComplaintHistory.builder()
                .complaint(complaint)
                .status(ComplaintStatus.IN_PROGRESS)
                .remarks("Reopened by citizen: " + cleanReason + (hasPhoto ? " (photo attached)" : ""))
                .updatedBy(user.getName())
                .build());
        if (hasPhoto) {
            attachmentService.addPhoto(complaint, user, AttachmentKind.REOPEN, photo);
        }

        if (complaint.getAssignedOfficial() != null) {
            notificationService.notify(complaint.getAssignedOfficial(),
                    "Complaint " + complaint.getComplaintNumber() + " was reopened by the citizen.",
                    complaint.getId());
        }

        auditLogService.log(user, "COMPLAINT_REOPENED", "Complaint", complaint.getId(),
                "Citizen reopened complaint " + complaint.getComplaintNumber() + " with reason: " + cleanReason
                        + (hasPhoto ? " (photo attached)" : ""));

        return withCompletionFlag(mapToResponse(complaint), complaint);
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

        auditLogService.log(user, "COMPLAINT_CLOSED", "Complaint", complaint.getId(),
                "Citizen closed complaint " + complaint.getComplaintNumber());

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

    /**
     * Permanently deletes a complaint together with everything that belongs
     * to it: status history, messages, feedback, attachments (records and
     * stored files) and the notifications that pointed at it.
     * <p>
     * This can't be undone, so the admin performing it must re-enter their
     * own password as a confirmation - same safeguard as the admin
     * password-reset action. The action is written to the audit log (which
     * keeps a plain-text summary, since the complaint itself no longer exists).
     */
    @Transactional
    public void deleteComplaint(Long id, String adminEmail, String adminPassword) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin account not found"));
        if (adminPassword == null || adminPassword.isEmpty()
                || !passwordEncoder.matches(adminPassword, admin.getPassword())) {
            throw new IllegalStateException("Incorrect password. The complaint was not deleted.");
        }

        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));

        String summary = complaint.getComplaintNumber() + " \"" + complaint.getTitle() + "\" (status "
                + complaint.getStatus() + ", filed by " + complaint.getUser().getName()
                + " <" + complaint.getUser().getEmail() + ">)";

        // Children first, then the complaint itself (its status history is
        // removed by the cascade on Complaint.history).
        notificationRepository.deleteAllByComplaintId(id);
        commentRepository.deleteAllByComplaintId(id);
        feedbackRepository.findByComplaintId(id).ifPresent(feedbackRepository::delete);
        attachmentService.deleteAllForComplaint(id);
        complaintRepository.delete(complaint);
        complaintRepository.flush();

        auditLogService.log(admin, "COMPLAINT_DELETED", "Complaint", id,
                "Admin permanently deleted complaint " + summary);
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
                .latitude(c.getLatitude())
                .longitude(c.getLongitude())
                .resolvedAddress(c.getResolvedAddress())
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
