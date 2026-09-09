package com.project.complaint.repository;

import com.project.complaint.entity.ComplaintComment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ComplaintCommentRepository extends JpaRepository<ComplaintComment, Long> {
    List<ComplaintComment> findByComplaintIdOrderByCreatedAtAsc(Long complaintId);
    long countByUserId(Long userId);
}
