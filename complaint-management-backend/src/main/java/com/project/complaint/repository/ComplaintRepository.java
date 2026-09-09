package com.project.complaint.repository;

import com.project.complaint.entity.Complaint;
import com.project.complaint.entity.ComplaintStatus;
import com.project.complaint.entity.Priority;
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
    List<Complaint> findByDepartmentId(Long departmentId);
    List<Complaint> findByCategory(String category);
    long countByCategory(String category);
}
