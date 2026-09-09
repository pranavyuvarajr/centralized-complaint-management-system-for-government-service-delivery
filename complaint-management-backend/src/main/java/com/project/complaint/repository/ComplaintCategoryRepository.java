package com.project.complaint.repository;

import com.project.complaint.entity.ComplaintCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ComplaintCategoryRepository extends JpaRepository<ComplaintCategory, Long> {
    boolean existsByName(String name);
    List<ComplaintCategory> findByActiveTrue();
}
