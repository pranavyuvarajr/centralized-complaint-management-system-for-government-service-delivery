package com.project.complaint.service;

import com.project.complaint.dto.DepartmentDto;
import com.project.complaint.entity.Complaint;
import com.project.complaint.entity.Department;
import com.project.complaint.entity.User;
import com.project.complaint.exception.ReassignmentRequiredException;
import com.project.complaint.repository.ComplaintRepository;
import com.project.complaint.repository.DepartmentRepository;
import com.project.complaint.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;

    public List<DepartmentDto.Response> getAllDepartments() {
        return departmentRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public DepartmentDto.Response createDepartment(DepartmentDto.CreateRequest request) {
        if (departmentRepository.existsByName(request.getName())) {
            throw new RuntimeException("Department already exists");
        }
        Department department = departmentRepository.save(
                Department.builder()
                        .name(request.getName())
                        .description(request.getDescription())
                        .contactEmail(request.getContactEmail())
                        .contactPhone(request.getContactPhone())
                        .active(true)
                        .build()
        );
        return mapToResponse(department);
    }

    public DepartmentDto.Response updateDepartment(Long id, DepartmentDto.CreateRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found"));
        department.setName(request.getName());
        department.setDescription(request.getDescription());
        department.setContactEmail(request.getContactEmail());
        department.setContactPhone(request.getContactPhone());
        return mapToResponse(departmentRepository.save(department));
    }

    @Transactional
    public DepartmentDto.Response toggleActive(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found"));
        department.setActive(!department.isActive());
        return mapToResponse(departmentRepository.save(department));
    }

    /**
     * Permanently deletes a department. If any complaints or officials still
     * point to it, the caller must supply targetDepartmentId to move them to
     * another department first — otherwise a ReassignmentRequiredException
     * lists the other active departments to choose from.
     */
    @Transactional
    public void deleteDepartment(Long id, Long targetDepartmentId) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        List<Complaint> complaints = complaintRepository.findByDepartmentId(id);
        List<User> officials = userRepository.findByDepartmentId(id);

        if (!complaints.isEmpty() || !officials.isEmpty()) {
            if (targetDepartmentId == null) {
                List<Map<String, Object>> options = departmentRepository.findAll().stream()
                        .filter(d -> d.isActive() && !d.getId().equals(id))
                        .map(d -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("id", d.getId());
                            m.put("name", d.getName());
                            return m;
                        }).collect(Collectors.toList());

                if (options.isEmpty()) {
                    throw new IllegalStateException(
                            "This department has " + complaints.size() + " complaint(s) and " + officials.size()
                                    + " official(s) linked to it, and there's no other active department to move them to. "
                                    + "Add another department first, or deactivate this one instead.");
                }

                throw new ReassignmentRequiredException(
                        "This department has " + complaints.size() + " complaint(s) and " + officials.size()
                                + " official(s) linked to it. Choose another department to move them to before deleting.",
                        complaints.size() + officials.size(), options);
            }

            Department target = departmentRepository.findById(targetDepartmentId)
                    .orElseThrow(() -> new RuntimeException("Selected department not found"));
            if (target.getId().equals(id)) {
                throw new IllegalStateException("Cannot reassign to the department being deleted.");
            }

            for (Complaint c : complaints) {
                c.setDepartment(target);
            }
            complaintRepository.saveAll(complaints);

            for (User u : officials) {
                u.setDepartment(target);
            }
            userRepository.saveAll(officials);
        }

        departmentRepository.delete(department);
    }

    private DepartmentDto.Response mapToResponse(Department d) {
        return DepartmentDto.Response.builder()
                .id(d.getId())
                .name(d.getName())
                .description(d.getDescription())
                .contactEmail(d.getContactEmail())
                .contactPhone(d.getContactPhone())
                .active(d.isActive())
                .build();
    }
}
