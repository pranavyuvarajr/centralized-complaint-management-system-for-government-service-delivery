package com.project.complaint.service;

import com.project.complaint.dto.UserDto;
import com.project.complaint.entity.Role;
import com.project.complaint.entity.User;
import com.project.complaint.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServicePhoneRulesTest {

    @Mock UserRepository userRepository;
    @Mock ComplaintRepository complaintRepository;
    @Mock ComplaintCommentRepository commentRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock ComplaintHistoryRepository historyRepository;
    @Mock NotificationService notificationService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UrgentAlertService urgentAlertService;
    @InjectMocks UserService service;

    User citizen, official;

    @BeforeEach
    void setUp() {
        citizen = User.builder().id(1L).name("Cit").email("c@x.com").phone("9000000001").role(Role.CITIZEN).build();
        official = User.builder().id(2L).name("Off").email("o@x.com").phone("9000000002").role(Role.OFFICIAL).build();
        when(userRepository.findByEmail("c@x.com")).thenReturn(Optional.of(citizen));
        when(userRepository.findByEmail("o@x.com")).thenReturn(Optional.of(official));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    private UserDto.UpdateProfileRequest profile(String name, String phone) {
        return new UserDto.UpdateProfileRequest(name, phone);
    }

    @Test void citizenCannotClearTheirNumber() {
        assertThrows(IllegalStateException.class, () -> service.updateProfile("c@x.com", profile("Cit", "")));
        assertEquals("9000000001", citizen.getPhone());
    }

    @Test void officialCanClearAndLaterAddANumber() {
        service.updateProfile("o@x.com", profile("Off", ""));
        assertNull(official.getPhone());
        service.updateProfile("o@x.com", profile("Off", "9876543210"));
        assertEquals("9876543210", official.getPhone());
    }

    @Test void officialNumberIsNotCheckedForDuplicates() {
        when(userRepository.existsByPhoneAndRoleAndIdNot(anyString(), any(), anyLong())).thenReturn(true);
        service.updateProfile("o@x.com", profile("Off", "9876543210"));
        assertEquals("9876543210", official.getPhone());
        verify(userRepository, never()).existsByPhoneAndRoleAndIdNot(anyString(), any(), anyLong());
    }

    @Test void citizenNumberIsCheckedAgainstCitizensOnly() {
        when(userRepository.existsByPhoneAndRoleAndIdNot("9876543210", Role.CITIZEN, 1L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.updateProfile("c@x.com", profile("Cit", "9876543210")));
        assertEquals("9000000001", citizen.getPhone());
    }

    @Test void citizenKeepingTheirOwnNumberSkipsTheDuplicateCheck() {
        service.updateProfile("c@x.com", profile("New Name", "9000000001"));
        verify(userRepository, never()).existsByPhoneAndRoleAndIdNot(anyString(), any(), anyLong());
        assertEquals("New Name", citizen.getName());
    }
}
