package com.project.complaint.service;

import com.project.complaint.dto.CategoryDto;
import com.project.complaint.entity.Complaint;
import com.project.complaint.entity.ComplaintCategory;
import com.project.complaint.exception.ReassignmentRequiredException;
import com.project.complaint.repository.ComplaintCategoryRepository;
import com.project.complaint.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComplaintCategoryService {

    private final ComplaintCategoryRepository categoryRepository;
    private final ComplaintRepository complaintRepository;

    public List<CategoryDto.Response> getActiveCategories() {
        return categoryRepository.findByActiveTrue().stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    public List<CategoryDto.Response> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    public CategoryDto.Response createCategory(CategoryDto.CreateRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new RuntimeException("Category already exists");
        }
        ComplaintCategory category = categoryRepository.save(ComplaintCategory.builder()
                .name(request.getName())
                .description(request.getDescription())
                .active(true)
                .build());
        return mapToResponse(category);
    }

    public CategoryDto.Response updateCategory(Long id, CategoryDto.CreateRequest request) {
        ComplaintCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        return mapToResponse(categoryRepository.save(category));
    }

    public CategoryDto.Response toggleActive(Long id) {
        ComplaintCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        category.setActive(!category.isActive());
        return mapToResponse(categoryRepository.save(category));
    }

    /**
     * Permanently deletes a category. If complaints already use this
     * category's name, the caller must supply targetCategoryId to move them
     * to another category first — otherwise a ReassignmentRequiredException
     * lists the other active categories to choose from.
     */
    @Transactional
    public void deleteCategory(Long id, Long targetCategoryId) {
        ComplaintCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        List<Complaint> complaints = complaintRepository.findByCategory(category.getName());

        if (!complaints.isEmpty()) {
            if (targetCategoryId == null) {
                List<Map<String, Object>> options = categoryRepository.findAll().stream()
                        .filter(c -> c.isActive() && !c.getId().equals(id))
                        .map(c -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("id", c.getId());
                            m.put("name", c.getName());
                            return m;
                        }).collect(Collectors.toList());

                if (options.isEmpty()) {
                    throw new IllegalStateException(
                            "This category is used by " + complaints.size() + " complaint(s), and there's no other "
                                    + "active category to move them to. Add another category first, or deactivate this one instead.");
                }

                throw new ReassignmentRequiredException(
                        "This category is used by " + complaints.size() + " complaint(s). Choose another category "
                                + "to move them to before deleting.",
                        complaints.size(), options);
            }

            ComplaintCategory target = categoryRepository.findById(targetCategoryId)
                    .orElseThrow(() -> new RuntimeException("Selected category not found"));
            if (target.getId().equals(id)) {
                throw new IllegalStateException("Cannot reassign to the category being deleted.");
            }

            for (Complaint c : complaints) {
                c.setCategory(target.getName());
            }
            complaintRepository.saveAll(complaints);
        }

        categoryRepository.delete(category);
    }

    private CategoryDto.Response mapToResponse(ComplaintCategory c) {
        return CategoryDto.Response.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .active(c.isActive())
                .build();
    }
}
