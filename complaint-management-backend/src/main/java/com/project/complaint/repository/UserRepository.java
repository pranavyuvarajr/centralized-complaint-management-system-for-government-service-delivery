package com.project.complaint.repository;

import com.project.complaint.entity.Role;
import com.project.complaint.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    boolean existsByPhoneAndIdNot(String phone, Long id);
    // Uniqueness applies to citizens only (officials and admins may share or omit numbers).
    boolean existsByPhoneAndRole(String phone, Role role);
    boolean existsByPhoneAndRoleAndIdNot(String phone, Role role, Long id);
    List<User> findByRole(Role role);
    List<User> findByDepartmentId(Long departmentId);
    Optional<User> findByAlertEmailTokenHash(String alertEmailTokenHash);
}
