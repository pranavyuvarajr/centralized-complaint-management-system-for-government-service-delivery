package com.project.complaint.repository;

import com.project.complaint.entity.ComplaintHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ComplaintHistoryRepository extends JpaRepository<ComplaintHistory, Long> {
    List<ComplaintHistory> findByComplaintIdOrderByUpdatedAtDesc(Long complaintId);
}
