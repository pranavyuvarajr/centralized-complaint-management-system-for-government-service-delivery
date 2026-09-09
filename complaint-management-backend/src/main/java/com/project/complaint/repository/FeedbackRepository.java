package com.project.complaint.repository;

import com.project.complaint.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    Optional<Feedback> findByComplaintId(Long complaintId);
    boolean existsByComplaintId(Long complaintId);
}
