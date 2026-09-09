package com.project.complaint.repository;

import com.project.complaint.entity.ComplaintAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ComplaintAttachmentRepository extends JpaRepository<ComplaintAttachment, Long> {
    List<ComplaintAttachment> findByComplaintIdOrderByUploadedAtAsc(Long complaintId);
}
