package com.project.complaint.service;

import com.project.complaint.config.UrgencyProperties;
import com.project.complaint.entity.*;
import com.project.complaint.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UrgentAlertServiceTest {

    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;
    @Mock ApplicationEventPublisher events;

    UrgentAlertService service;
    User official, adminVerified, adminUnverified, adminUnset, adminInactive;
    Complaint complaint;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        service = new UrgentAlertService(userRepository, notificationService, events, new UrgencyProperties(), clock);

        official = User.builder().id(2L).name("Asha").role(Role.OFFICIAL).active(true).build();
        adminVerified = User.builder().id(11L).name("A1").role(Role.ADMIN).active(true)
                .alertEmail("a1@example.com").alertEmailVerified(true).build();
        adminUnverified = User.builder().id(12L).name("A2").role(Role.ADMIN).active(true)
                .alertEmail("a2@example.com").alertEmailVerified(false).build();
        adminUnset = User.builder().id(13L).name("A3").role(Role.ADMIN).active(true).build();
        adminInactive = User.builder().id(14L).name("A4").role(Role.ADMIN).active(false)
                .alertEmail("a4@example.com").alertEmailVerified(true).build();
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(adminVerified, adminUnverified, adminUnset, adminInactive));

        complaint = Complaint.builder().id(10L).complaintNumber("CMP-10").status(ComplaintStatus.SUBMITTED)
                .urgencyStatus(UrgencyStatus.PENDING).build();
    }

    private UrgentAlertRequested publishedEvent() {
        ArgumentCaptor<UrgentAlertRequested> cap = ArgumentCaptor.forClass(UrgentAlertRequested.class);
        verify(events).publishEvent(cap.capture());
        return cap.getValue();
    }

    @Test void assignedOfficialWithVerifiedEmailGetsEmailOnlyNoExtraInAppNotification() {
        official.setAlertEmail("o@example.com"); official.setAlertEmailVerified(true);
        complaint.setAssignedOfficial(official);
        service.onSubmitted(complaint);
        UrgentAlertRequested e = publishedEvent();
        assertEquals(UrgentAlertRequested.Kind.INITIAL, e.kind());
        assertEquals(List.of(2L), e.recipientUserIds());
        verifyNoInteractions(notificationService); // the URGENT in-app assignment message is sent by the caller
    }

    @Test void assignedOfficialWithoutVerifiedEmailGetsNoLoginEmailFallbackAndNoExtraNotification() {
        official.setAlertEmail("o@example.com"); official.setAlertEmailVerified(false); // unverified = unset
        complaint.setAssignedOfficial(official);
        service.onSubmitted(complaint);
        verify(events, never()).publishEvent(any(Object.class));
        verifyNoInteractions(notificationService);
    }

    @Test void unassignedAlertsEveryActiveAdminByVerifiedEmailElseInApp() {
        service.onSubmitted(complaint);
        UrgentAlertRequested e = publishedEvent();
        assertEquals(List.of(11L), e.recipientUserIds());               // only the verified admin gets email
        verify(notificationService).notify(eq(adminUnverified), contains("URGENT"), eq(10L)); // unverified -> in-app
        verify(notificationService).notify(eq(adminUnset), contains("URGENT"), eq(10L));      // unset -> in-app
        verify(notificationService, never()).notify(eq(adminInactive), anyString(), anyLong());
        verify(notificationService, never()).notify(eq(adminVerified), anyString(), anyLong());
    }

    @Test void reminderGoesToOfficialByEmailOrInApp() {
        complaint.setAssignedOfficial(official);
        service.sendReminder(complaint, 2, 12);
        verify(notificationService).notify(eq(official), contains("Reminder 2 of 4"), eq(10L)); // no verified email -> in-app
        verify(events, never()).publishEvent(any(Object.class));

        reset(notificationService, events);
        official.setAlertEmail("o@example.com"); official.setAlertEmailVerified(true);
        service.sendReminder(complaint, 3, 18);
        UrgentAlertRequested e = publishedEvent();
        assertEquals(UrgentAlertRequested.Kind.REMINDER, e.kind());
        assertEquals(3, e.reminderNumber());
        assertEquals(4, e.reminderTotal());
        verifyNoInteractions(notificationService);
    }

    @Test void unassignedReminderGoesToAdmins() {
        service.sendReminder(complaint, 1, 6);
        assertEquals(List.of(11L), publishedEvent().recipientUserIds());
        verify(notificationService).notify(eq(adminUnset), contains("Reminder 1 of 4"), eq(10L));
    }

    @Test void escalationGoesToEveryActiveAdminAndMentionsFourReminders() {
        complaint.setAssignedOfficial(official);
        service.sendEscalation(complaint, 24);
        UrgentAlertRequested e = publishedEvent();
        assertEquals(UrgentAlertRequested.Kind.ESCALATION, e.kind());
        assertEquals(List.of(11L), e.recipientUserIds());
        verify(notificationService).notify(eq(adminUnverified), contains("reminded 4 times over 24 hours"), eq(10L));
    }

    @Test void reassignmentResetsReminderStateAndRestartsClock() {
        complaint.setAssignedOfficial(official);
        complaint.setReminderCount(3);
        complaint.setLastReminderAt(java.time.LocalDateTime.of(2025, 12, 31, 1, 0));
        complaint.setAdminEscalatedAt(java.time.LocalDateTime.of(2025, 12, 31, 2, 0));
        service.onReassigned(complaint);
        assertEquals(0, complaint.getReminderCount());
        assertNull(complaint.getLastReminderAt());
        assertNull(complaint.getAdminEscalatedAt());
        assertEquals(java.time.LocalDateTime.of(2026, 1, 1, 10, 0), complaint.getUrgencyAssignedAt());
    }
}
