package com.project.complaint.specification;

import com.project.complaint.entity.Complaint;
import com.project.complaint.entity.ComplaintStatus;
import com.project.complaint.entity.Priority;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ComplaintSpecifications {

    public static Specification<Complaint> build(Long userId, Long departmentId, Long assignedOfficialId,
                                                   ComplaintStatus status, String category, Priority priority,
                                                   String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) predicates.add(cb.equal(root.get("user").get("id"), userId));
            if (departmentId != null) predicates.add(cb.equal(root.get("department").get("id"), departmentId));
            if (assignedOfficialId != null) predicates.add(cb.equal(root.get("assignedOfficial").get("id"), assignedOfficialId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.toLowerCase()));
            }
            if (priority != null) predicates.add(cb.equal(root.get("priority"), priority));
            if (search != null && !search.isBlank()) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("complaintNumber")), like)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
