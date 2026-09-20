package com.project.complaint.service;

import com.project.complaint.dto.AuthDto;
import com.project.complaint.entity.Role;
import com.project.complaint.entity.User;
import com.project.complaint.repository.DepartmentRepository;
import com.project.complaint.repository.UserRepository;
import com.project.complaint.security.JwtUtil;
import com.project.complaint.security.LoginRateLimiter;
import com.project.complaint.util.PhoneUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginRateLimiter loginRateLimiter;
    private final AuditLogService auditLogService;

    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // Mobile number is mandatory for every account (citizen, official, admin).
        String phone = PhoneUtil.requireValid(request.getPhone());
        // ...and each number can belong to only one account.
        if (userRepository.existsByPhone(phone)) {
            throw new RuntimeException(PhoneUtil.DUPLICATE_MESSAGE);
        }

        Role role = Role.CITIZEN;
        if (request.getRole() != null) {
            try { role = Role.valueOf(request.getRole().toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }

        User.UserBuilder builder = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(phone)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .active(true);

        if (role == Role.OFFICIAL && request.getDepartmentId() != null) {
            departmentRepository.findById(request.getDepartmentId())
                    .ifPresent(builder::department);
        }

        User user = userRepository.save(builder.build());
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        auditLogService.log(user, "USER_REGISTERED", "User", user.getId(),
                user.getName() + " (" + user.getEmail() + ") registered as " + user.getRole().name());

        return AuthDto.AuthResponse.builder()
                .token(token)
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        String key = request.getEmail().toLowerCase();
        loginRateLimiter.checkAllowed(key);

        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginRateLimiter.recordFailure(key);
            auditLogService.logByEmail(request.getEmail(), "LOGIN_FAILED", "User", null,
                    "Failed login attempt for " + request.getEmail() + " (invalid credentials)");
            throw new RuntimeException("Invalid email or password");
        }
        if (!user.isActive()) {
            auditLogService.log(user, "LOGIN_FAILED", "User", user.getId(),
                    user.getName() + " (" + user.getEmail() + ") attempted to log in to a deactivated account");
            throw new RuntimeException("This account has been deactivated. Contact the administrator.");
        }

        loginRateLimiter.recordSuccess(key);
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        auditLogService.log(user, "LOGIN_SUCCESS", "User", user.getId(),
                user.getName() + " (" + user.getEmail() + ") logged in");

        return AuthDto.AuthResponse.builder()
                .token(token)
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}
