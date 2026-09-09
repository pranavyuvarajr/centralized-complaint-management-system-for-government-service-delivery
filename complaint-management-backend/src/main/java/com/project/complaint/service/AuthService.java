package com.project.complaint.service;

import com.project.complaint.dto.AuthDto;
import com.project.complaint.entity.Role;
import com.project.complaint.entity.User;
import com.project.complaint.repository.DepartmentRepository;
import com.project.complaint.repository.UserRepository;
import com.project.complaint.security.JwtUtil;
import com.project.complaint.security.LoginRateLimiter;
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

    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        Role role = Role.CITIZEN;
        if (request.getRole() != null) {
            try { role = Role.valueOf(request.getRole().toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }

        User.UserBuilder builder = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .active(true);

        if (role == Role.OFFICIAL && request.getDepartmentId() != null) {
            departmentRepository.findById(request.getDepartmentId())
                    .ifPresent(builder::department);
        }

        User user = userRepository.save(builder.build());
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

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
            throw new RuntimeException("Invalid email or password");
        }
        if (!user.isActive()) {
            throw new RuntimeException("This account has been deactivated. Contact the administrator.");
        }

        loginRateLimiter.recordSuccess(key);
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        return AuthDto.AuthResponse.builder()
                .token(token)
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}
