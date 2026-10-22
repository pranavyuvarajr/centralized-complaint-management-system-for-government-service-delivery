package com.project.complaint.service;

import com.project.complaint.dto.AuthDto;
import com.project.complaint.entity.Role;
import com.project.complaint.entity.User;
import com.project.complaint.repository.DepartmentRepository;
import com.project.complaint.repository.UserRepository;
import com.project.complaint.security.JwtUtil;
import com.project.complaint.security.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServicePhoneRulesTest {

    @Mock UserRepository userRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock LoginRateLimiter loginRateLimiter;
    @Mock AuditLogService auditLogService;
    @InjectMocks AuthService service;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("token");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(99L);
            return u;
        });
    }

    private AuthDto.RegisterRequest request(String role, String phone) {
        return new AuthDto.RegisterRequest("Name", "n@example.com", phone, "secret1", role, null);
    }

    private User savedUser() {
        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        return cap.getValue();
    }

    // ---- public self-registration: unchanged strictness
    @Test void publicCitizenRegistrationStillRequiresANumber() {
        assertThrows(IllegalStateException.class, () -> service.register(request("CITIZEN", null)));
        assertThrows(IllegalStateException.class, () -> service.register(request(null, "")));
        verify(userRepository, never()).save(any());
    }

    @Test void publicRegistrationClaimingStaffRoleCannotSkipTheNumber() {
        assertThrows(IllegalStateException.class, () -> service.register(request("OFFICIAL", null)));
        assertThrows(IllegalStateException.class, () -> service.register(request("ADMIN", "")));
        verify(userRepository, never()).save(any());
    }

    @Test void citizenNumberMustBeUniqueAmongCitizens() {
        when(userRepository.existsByPhoneAndRole("9876543210", Role.CITIZEN)).thenReturn(true);
        assertThrows(RuntimeException.class, () -> service.register(request("CITIZEN", "9876543210")));
        verify(userRepository, never()).save(any());
    }

    @Test void citizenMayUseANumberOnlyAnOfficialOrAdminHas() {
        // existsByPhoneAndRole(.., CITIZEN) is false: the other holder is staff, which does not count
        service.register(request("CITIZEN", "9876543210"));
        assertEquals("9876543210", savedUser().getPhone());
    }

    // ---- admin-created accounts
    @Test void adminCanCreateOfficialAndAdminWithoutANumber() {
        service.register(request("OFFICIAL", null), true);
        assertNull(savedUser().getPhone());
        reset(userRepository);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        service.register(request("ADMIN", "  "), true);
        assertNull(savedUser().getPhone());
    }

    @Test void adminCreatedCitizenStillNeedsANumber() {
        assertThrows(IllegalStateException.class, () -> service.register(request("CITIZEN", null), true));
        verify(userRepository, never()).save(any());
    }

    @Test void adminCreatedOfficialNumberIfGivenMustBeValidButNeedNotBeUnique() {
        assertThrows(IllegalStateException.class, () -> service.register(request("OFFICIAL", "123"), true));
        when(userRepository.existsByPhoneAndRole(anyString(), any())).thenReturn(true); // a citizen has it
        service.register(request("OFFICIAL", "9876543210"), true);
        assertEquals("9876543210", savedUser().getPhone());
        verify(userRepository, never()).existsByPhoneAndRole(anyString(), any());
    }
}
