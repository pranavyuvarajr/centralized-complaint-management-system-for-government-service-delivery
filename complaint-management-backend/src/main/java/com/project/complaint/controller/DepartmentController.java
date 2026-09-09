package com.project.complaint.controller;

import com.project.complaint.dto.DepartmentDto;
import com.project.complaint.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    public ResponseEntity<List<DepartmentDto.Response>> getAll() {
        return ResponseEntity.ok(departmentService.getAllDepartments());
    }

    @PostMapping
    public ResponseEntity<DepartmentDto.Response> create(@Valid @RequestBody DepartmentDto.CreateRequest request) {
        return ResponseEntity.ok(departmentService.createDepartment(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DepartmentDto.Response> update(@PathVariable Long id,
                                                          @Valid @RequestBody DepartmentDto.CreateRequest request) {
        return ResponseEntity.ok(departmentService.updateDepartment(id, request));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<DepartmentDto.Response> toggle(@PathVariable Long id) {
        return ResponseEntity.ok(departmentService.toggleActive(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                        @RequestParam(required = false) Long reassignTo) {
        departmentService.deleteDepartment(id, reassignTo);
        return ResponseEntity.noContent().build();
    }
}
