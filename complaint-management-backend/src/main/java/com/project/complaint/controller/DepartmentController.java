package com.project.complaint.controller;

import com.project.complaint.dto.DepartmentDto;
import com.project.complaint.service.AuditLogService;
import com.project.complaint.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;
    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<List<DepartmentDto.Response>> getAll() {
        return ResponseEntity.ok(departmentService.getAllDepartments());
    }

    @PostMapping
    public ResponseEntity<DepartmentDto.Response> create(@Valid @RequestBody DepartmentDto.CreateRequest request,
                                                           Authentication auth) {
        DepartmentDto.Response saved = departmentService.createDepartment(request);
        auditLogService.logByEmail(auth.getName(), "DEPARTMENT_CREATED", "Department", saved.getId(),
                "Admin created department \"" + saved.getName() + "\"");
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DepartmentDto.Response> update(@PathVariable Long id,
                                                          @Valid @RequestBody DepartmentDto.CreateRequest request,
                                                          Authentication auth) {
        DepartmentDto.Response saved = departmentService.updateDepartment(id, request);
        auditLogService.logByEmail(auth.getName(), "DEPARTMENT_UPDATED", "Department", saved.getId(),
                "Admin updated department \"" + saved.getName() + "\"");
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<DepartmentDto.Response> toggle(@PathVariable Long id, Authentication auth) {
        DepartmentDto.Response saved = departmentService.toggleActive(id);
        auditLogService.logByEmail(auth.getName(), saved.isActive() ? "DEPARTMENT_ACTIVATED" : "DEPARTMENT_DEACTIVATED",
                "Department", saved.getId(),
                "Admin " + (saved.isActive() ? "activated" : "deactivated") + " department \"" + saved.getName() + "\"");
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                        @RequestParam(required = false) Long reassignTo,
                                        Authentication auth) {
        List<DepartmentDto.Response> before = departmentService.getAllDepartments();
        String name = before.stream().filter(d -> d.getId().equals(id)).findFirst()
                .map(DepartmentDto.Response::getName).orElse("#" + id);
        departmentService.deleteDepartment(id, reassignTo);
        auditLogService.logByEmail(auth.getName(), "DEPARTMENT_DELETED", "Department", id,
                "Admin permanently deleted department \"" + name + "\""
                        + (reassignTo != null ? ", reassigning its complaints and officials to department #" + reassignTo : ""));
        return ResponseEntity.noContent().build();
    }
}
