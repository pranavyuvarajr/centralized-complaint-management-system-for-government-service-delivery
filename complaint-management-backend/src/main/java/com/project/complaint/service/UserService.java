package com.project.complaint.service;

import com.project.complaint.dto.UserDto;
import com.project.complaint.entity.Complaint;
import com.project.complaint.entity.Role;
import com.project.complaint.entity.User;
import com.project.complaint.exception.ReassignmentRequiredException;
import com.project.complaint.repository.ComplaintCommentRepository;
import com.project.complaint.repository.ComplaintRepository;
import com.project.complaint.repository.NotificationRepository;
import com.project.complaint.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final ComplaintRepository complaintRepository;
    private final ComplaintCommentRepository commentRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;

    /** Used by callers (e.g. audit logging) that need the user's display details before/without a full DTO list scan. */
    public UserDto.Response getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToResponse(user);
    }

    public List<UserDto.Response> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<UserDto.Response> getUsersByRole(String role) {
        return userRepository.findByRole(Role.valueOf(role.toUpperCase())).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserDto.Response setActive(Long id, boolean active) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(active);
        return mapToResponse(userRepository.save(user));
    }

    /**
     * Permanently deletes a user.
     * - Citizen-authored complaints can never be reassigned (there's no
     *   sensible "other citizen" to move authorship to), so a citizen with
     *   any complaints on file can't be deleted — deactivate instead.
     * - An official's assigned complaints CAN be moved to another official
     *   in the same department. If targetOfficialId is not provided and the
     *   official has assigned complaints, a ReassignmentRequiredException is
     *   thrown listing the other active officials in that same department.
     */
    @Transactional
    public void deleteUser(Long id, Long targetOfficialId, String currentAdminEmail) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getEmail().equalsIgnoreCase(currentAdminEmail)) {
            throw new IllegalStateException("You cannot delete your own account.");
        }

        long asCitizen = complaintRepository.countByUserId(id);
        if (asCitizen > 0) {
            throw new IllegalStateException(
                    "This user has filed " + asCitizen + " complaint(s). Complaints can't be reassigned to another "
                            + "citizen, so this account can't be permanently deleted — deactivate it instead to preserve that history.");
        }

        long authoredComments = commentRepository.countByUserId(id);
        if (authoredComments > 0) {
            throw new IllegalStateException(
                    "This user has posted " + authoredComments + " message(s) on complaints. Those messages can't be "
                            + "reassigned to someone else, so this account can't be permanently deleted — deactivate it "
                            + "instead to preserve that conversation history.");
        }

        List<Complaint> assigned = complaintRepository.findByAssignedOfficialId(id);
        if (!assigned.isEmpty()) {
            if (targetOfficialId == null) {
                Long deptId = user.getDepartment() != null ? user.getDepartment().getId() : null;
                List<User> candidates = deptId == null ? List.of()
                        : userRepository.findByDepartmentId(deptId).stream()
                                .filter(u -> u.getRole() == Role.OFFICIAL && u.isActive() && !u.getId().equals(id))
                                .collect(Collectors.toList());

                if (candidates.isEmpty()) {
                    throw new IllegalStateException(
                            "This official has " + assigned.size() + " complaint(s) assigned to them, and there's no "
                                    + "other active official in the same department to hand them to. Add another official "
                                    + "to the department first, or deactivate this account instead.");
                }

                List<Map<String, Object>> options = candidates.stream().map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("name", u.getName());
                    return m;
                }).collect(Collectors.toList());

                throw new ReassignmentRequiredException(
                        "This official has " + assigned.size() + " complaint(s) assigned to them. "
                                + "Choose another official in the same department to reassign them to before deleting.",
                        assigned.size(), options);
            }

            User target = userRepository.findById(targetOfficialId)
                    .orElseThrow(() -> new RuntimeException("Selected official not found"));
            Long deptId = user.getDepartment() != null ? user.getDepartment().getId() : null;
            Long targetDeptId = target.getDepartment() != null ? target.getDepartment().getId() : null;
            if (target.getRole() != Role.OFFICIAL) {
                throw new IllegalStateException("The reassignment target must be an official.");
            }
            if (deptId == null || targetDeptId == null || !deptId.equals(targetDeptId)) {
                throw new IllegalStateException("The reassignment target must belong to the same department.");
            }
            if (target.getId().equals(id)) {
                throw new IllegalStateException("Cannot reassign complaints to the account being deleted.");
            }

            for (Complaint c : assigned) {
                c.setAssignedOfficial(target);
            }
            complaintRepository.saveAll(assigned);
        }

        // Notifications are personal/ephemeral (not shared history like comments or
        // complaints), so it's safe to remove the user's own notifications rather than
        // blocking the delete. This is what was tripping the FK constraint before.
        notificationRepository.deleteAllByUserId(id);

        userRepository.delete(user);
    }

    public UserDto.Response getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToResponse(user);
    }

    @Transactional
    public UserDto.Response updateProfile(String email, UserDto.UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setName(request.getName());
        user.setPhone(request.getPhone());
        return mapToResponse(userRepository.save(user));
    }

    @Transactional
    public void changePassword(String email, UserDto.ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    /**
     * Admin override: sets a target user's password directly (they don't need
     * to know or provide their own old password). As a safety check, the
     * ADMIN performing this action must confirm it's really them by
     * re-entering their own current password.
     */
    @Transactional
    public void adminSetPassword(Long id, String newPassword, String adminEmail, String adminCurrentPassword) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin account not found"));
        if (adminCurrentPassword == null || !passwordEncoder.matches(adminCurrentPassword, admin.getPassword())) {
            throw new IllegalStateException("Your current password is incorrect.");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalStateException("New password must be at least 6 characters long.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private UserDto.Response mapToResponse(User u) {
        return UserDto.Response.builder()
                .id(u.getId())
                .name(u.getName())
                .email(u.getEmail())
                .phone(u.getPhone())
                .role(u.getRole().name())
                .departmentName(u.getDepartment() != null ? u.getDepartment().getName() : null)
                .active(u.isActive())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
