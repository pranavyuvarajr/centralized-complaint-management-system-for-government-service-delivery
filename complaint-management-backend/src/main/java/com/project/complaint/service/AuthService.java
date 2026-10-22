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

    /** Public self-registration: the mobile number is mandatory, whatever role the request names. */
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        return register(request, false);
    }

    /**
     * @param createdByAdmin true only for the admin "create user" endpoint. Then officials and admins may be created
     *                       without a mobile number (they can add it in their profile); citizens always need one.
     */
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request, boolean createdByAdmin) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        Role role = Role.CITIZEN;
        if (request.getRole() != null) {
            try { role = Role.valueOf(request.getRole().toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }

        // Mobile number: mandatory for citizens (and for any public registration); optional only for
        // officials/admins created by an admin.
        String phone = createdByAdmin
                ? PhoneUtil.normalizeForRole(role, request.getPhone())
                : PhoneUtil.requireValid(request.getPhone());
        // Uniqueness applies to citizens only.
        if (phone != null && PhoneUtil.mustBeUnique(role) && userRepository.existsByPhoneAndRole(phone, Role.CITIZEN)) {
            throw new RuntimeException(PhoneUtil.DUPLICATE_MESSAGE);
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
