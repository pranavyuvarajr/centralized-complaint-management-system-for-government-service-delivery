package com.project.complaint.repository;

import com.project.complaint.entity.Complaint;
import com.project.complaint.entity.ComplaintStatus;
import com.project.complaint.entity.Priority;
import com.project.complaint.entity.UrgencyStatus;
import com.project.complaint.entity.User;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ComplaintRepository extends JpaRepository<Complaint, Long>, JpaSpecificationExecutor<Complaint> {
    List<Complaint> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Complaint> findByDepartmentIdOrderByCreatedAtDesc(Long departmentId);
    List<Complaint> findByStatusOrderByCreatedAtDesc(ComplaintStatus status);
    List<Complaint> findAllByOrderByCreatedAtDesc();
    Optional<Complaint> findByComplaintNumber(String complaintNumber);
    long countByStatus(ComplaintStatus status);
    long countByDepartmentId(Long departmentId);
    long countByUserId(Long userId);
    long countByAssignedOfficialId(Long officialId);
    long countByPriority(Priority priority);
    long countByCreatedAtAfter(LocalDateTime dateTime);
    List<Complaint> findByAssignedOfficialId(Long officialId);
    List<Complaint> findByAssignedOfficialIdAndStatusNotIn(Long officialId, List<ComplaintStatus> statuses);
    List<Complaint> findByDepartmentId(Long departmentId);
    List<Complaint> findByCategory(String category);
    long countByCategory(String category);
    long countByAssignedOfficialIdAndStatusNotIn(Long officialId, List<ComplaintStatus> statuses);

    // ---- Urgent-complaint feature ----

    /**
     * First-decision-wins: only succeeds while the complaint is still PENDING.
     * Returns the number of rows changed (0 = someone decided first, or it was never pending).
     * Bulk update bypasses @PreUpdate, hence the explicit updatedAt.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Complaint c set c.urgencyStatus = :to, c.urgencyDecidedBy = :by, c.urgencyDecidedAt = :at, "
            + "c.urgencyDecisionNote = :note, c.priority = :priority, c.updatedAt = :at "
            + "where c.id = :id and c.urgencyStatus = com.project.complaint.entity.UrgencyStatus.PENDING")
    int decideIfPending(@Param("id") Long id, @Param("to") UrgencyStatus to, @Param("by") User by,
                        @Param("at") LocalDateTime at, @Param("note") String note,
                        @Param("priority") Priority priority);

    /** Cheap, indexed (urgency_status) scan used by the reminder job. */
    @Query("select c.id from Complaint c where c.urgencyStatus = com.project.complaint.entity.UrgencyStatus.PENDING "
            + "and c.status not in :finalStatuses order by c.id")
    List<Long> findPendingUrgentIds(@Param("finalStatuses") List<ComplaintStatus> finalStatuses);

    /** Claims reminder number {@code expected + 1}; 0 rows = already claimed, decided, reset or finished. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Complaint c set c.reminderCount = :next, c.lastReminderAt = :at "
            + "where c.id = :id and c.reminderCount = :expected and c.urgencyAssignedAt = :anchor "
            + "and c.urgencyStatus = com.project.complaint.entity.UrgencyStatus.PENDING "
            + "and c.status not in :finalStatuses")
    int claimReminder(@Param("id") Long id, @Param("expected") int expected, @Param("next") int next,
                      @Param("at") LocalDateTime at, @Param("anchor") LocalDateTime anchor,
                      @Param("finalStatuses") List<ComplaintStatus> finalStatuses);

    /** Claims the one-time admin escalation; 0 rows = already escalated, decided, reset or finished. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Complaint c set c.adminEscalatedAt = :at "
            + "where c.id = :id and c.adminEscalatedAt is null and c.reminderCount = :count "
            + "and c.urgencyAssignedAt = :anchor "
            + "and c.urgencyStatus = com.project.complaint.entity.UrgencyStatus.PENDING "
            + "and c.status not in :finalStatuses")
    int claimEscalation(@Param("id") Long id, @Param("count") int count, @Param("at") LocalDateTime at,
                        @Param("anchor") LocalDateTime anchor,
                        @Param("finalStatuses") List<ComplaintStatus> finalStatuses);
}
