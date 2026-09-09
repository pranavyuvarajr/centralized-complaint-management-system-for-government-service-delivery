package com.project.complaint.controller;

import com.project.complaint.dto.CategoryDto;
import com.project.complaint.service.ComplaintCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class ComplaintCategoryController {

    private final ComplaintCategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryDto.Response>> getActive() {
        return ResponseEntity.ok(categoryService.getActiveCategories());
    }

    @GetMapping("/all")
    public ResponseEntity<List<CategoryDto.Response>> getAll() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }

    @PostMapping
    public ResponseEntity<CategoryDto.Response> create(@Valid @RequestBody CategoryDto.CreateRequest request) {
        return ResponseEntity.ok(categoryService.createCategory(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryDto.Response> update(@PathVariable Long id, @Valid @RequestBody CategoryDto.CreateRequest request) {
        return ResponseEntity.ok(categoryService.updateCategory(id, request));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<CategoryDto.Response> toggle(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.toggleActive(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                        @RequestParam(required = false) Long reassignTo) {
        categoryService.deleteCategory(id, reassignTo);
        return ResponseEntity.noContent().build();
    }
}
