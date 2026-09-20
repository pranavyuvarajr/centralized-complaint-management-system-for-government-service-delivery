package com.project.complaint.controller;

import com.project.complaint.dto.CategoryDto;
import com.project.complaint.service.AuditLogService;
import com.project.complaint.service.ComplaintCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class ComplaintCategoryController {

    private final ComplaintCategoryService categoryService;
    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<List<CategoryDto.Response>> getActive() {
        return ResponseEntity.ok(categoryService.getActiveCategories());
    }

    @GetMapping("/all")
    public ResponseEntity<List<CategoryDto.Response>> getAll() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }

    @PostMapping
    public ResponseEntity<CategoryDto.Response> create(@Valid @RequestBody CategoryDto.CreateRequest request,
                                                         Authentication auth) {
        CategoryDto.Response saved = categoryService.createCategory(request);
        auditLogService.logByEmail(auth.getName(), "CATEGORY_CREATED", "ComplaintCategory", saved.getId(),
                "Admin created category \"" + saved.getName() + "\""
                        + (saved.getDepartmentName() != null ? ", auto-routed to " + saved.getDepartmentName() : ", no auto-routing")
                        + ", photo " + (saved.isImageRequired() ? "required" : "optional")
                        + ", location " + (saved.isLocationRequired() ? "required" : "optional") + " for citizens.");
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryDto.Response> update(@PathVariable Long id, @Valid @RequestBody CategoryDto.CreateRequest request,
                                                         Authentication auth) {
        CategoryDto.Response saved = categoryService.updateCategory(id, request);
        auditLogService.logByEmail(auth.getName(), "CATEGORY_UPDATED", "ComplaintCategory", saved.getId(),
                "Admin updated category \"" + saved.getName() + "\""
                        + (saved.getDepartmentName() != null ? ", auto-routed to " + saved.getDepartmentName() : ", no auto-routing")
                        + ", photo " + (saved.isImageRequired() ? "required" : "optional")
                        + ", location " + (saved.isLocationRequired() ? "required" : "optional") + " for citizens.");
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<CategoryDto.Response> toggle(@PathVariable Long id, Authentication auth) {
        CategoryDto.Response saved = categoryService.toggleActive(id);
        auditLogService.logByEmail(auth.getName(), saved.isActive() ? "CATEGORY_ACTIVATED" : "CATEGORY_DEACTIVATED",
                "ComplaintCategory", saved.getId(),
                "Admin " + (saved.isActive() ? "activated" : "deactivated") + " category \"" + saved.getName() + "\"");
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                        @RequestParam(required = false) Long reassignTo,
                                        Authentication auth) {
        List<CategoryDto.Response> before = categoryService.getAllCategories();
        String name = before.stream().filter(c -> c.getId().equals(id)).findFirst()
                .map(CategoryDto.Response::getName).orElse("#" + id);
        categoryService.deleteCategory(id, reassignTo);
        auditLogService.logByEmail(auth.getName(), "CATEGORY_DELETED", "ComplaintCategory", id,
                "Admin permanently deleted category \"" + name + "\""
                        + (reassignTo != null ? ", reassigning its existing complaints to category #" + reassignTo : ""));
        return ResponseEntity.noContent().build();
    }
}
