package com.project.complaint.service;

import com.project.complaint.dto.ComplaintDto;
import com.project.complaint.entity.*;
import com.project.complaint.repository.ComplaintHistoryRepository;
import com.project.complaint.repository.ComplaintRepository;
import com.project.complaint.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UrgencyServiceTest {

    @Mock ComplaintRepository complaintRepository;
    @Mock ComplaintHistoryRepository historyRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;
    @Mock AuditLogService auditLogService;

    UrgencyService service;
    User citizen, assigned, otherOfficial, admin;
    Complaint complaint;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        service = new UrgencyService(complaintRepository, historyRepository, userRepository,
                notificationService, auditLogService, clock);

        citizen = User.builder().id(1L).name("Cit").email("c@x.com").phone("9876543210").role(Role.CITIZEN).build();
        assigned = User.builder().id(2L).name("Asha").email("a@x.com").role(Role.OFFICIAL).build();
        otherOfficial = User.builder().id(3L).name("Ben").email("b@x.com").role(Role.OFFICIAL).build();
        admin = User.builder().id(4L).name("Root").email("r@x.com").role(Role.ADMIN).build();

        complaint = Complaint.builder().id(10L).complaintNumber("CMP-2026-000010").user(citizen)
                .assignedOfficial(assigned).status(ComplaintStatus.ASSIGNED).priority(Priority.URGENT)
                .urgencyStatus(UrgencyStatus.PENDING).urgencyReason("x".repeat(25)).wasInitiallyUrgent(true).build();

        when(complaintRepository.findById(10L)).thenReturn(Optional.of(complaint));
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(assigned));
        when(userRepository.findByEmail("b@x.com")).thenReturn(Optional.of(otherOfficial));
        when(userRepository.findByEmail("r@x.com")).thenReturn(Optional.of(admin));
        when(complaintRepository.decideIfPending(anyLong(), any(), any(), any(), any(), any())).thenReturn(1);
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ---- submission validation
    @Test void normalComplaintIgnoresUrgencyInput() {
        assertNull(service.validateUrgentRequest(false, "anything at all that is long enough", citizen));
    }
    @Test void urgentNeedsReasonOfTwentyToThousandChars() {
        assertThrows(RuntimeException.class, () -> service.validateUrgentRequest(true, "too short", citizen));
        assertThrows(RuntimeException.class, () -> service.validateUrgentRequest(true, "x".repeat(1001), citizen));
        assertEquals("x".repeat(20), service.validateUrgentRequest(true, "  " + "x".repeat(20) + "  ", citizen));
    }
    @Test void urgentNeedsPhoneOnAccount() {
        citizen.setPhone(null);
        assertThrows(RuntimeException.class, () -> service.validateUrgentRequest(true, "x".repeat(30), citizen));
    }

    // ---- decision
    @Test void assignedOfficialConfirmsUrgent() {
        service.decide(10L, "a@x.com", "OFFICIAL", "CONFIRM_URGENT", null);
        verify(complaintRepository).decideIfPending(eq(10L), eq(UrgencyStatus.CONFIRMED_URGENT), eq(assigned),
                any(), isNull(), eq(Priority.URGENT));
        verify(historyRepository).save(any(ComplaintHistory.class));
        verify(auditLogService).log(eq(assigned), eq("URGENCY_CONFIRMED"), eq("Complaint"), eq(10L), anyString());
    }
    @Test void markNormalRequiresReasonAndSetsMedium() {
        assertThrows(RuntimeException.class, () -> service.decide(10L, "a@x.com", "OFFICIAL", "MARK_NORMAL", "short"));
        verify(complaintRepository, never()).decideIfPending(anyLong(), any(), any(), any(), any(), any());
        service.decide(10L, "a@x.com", "OFFICIAL", "MARK_NORMAL", "Citizen did not answer the call");
        verify(complaintRepository).decideIfPending(eq(10L), eq(UrgencyStatus.DOWNGRADED_TO_NORMAL), eq(assigned),
                any(), eq("Citizen did not answer the call"), eq(Priority.MEDIUM));
    }
    @Test void otherOfficialCannotDecide() {
        assertThrows(RuntimeException.class, () -> service.decide(10L, "b@x.com", "OFFICIAL", "CONFIRM_URGENT", null));
    }
    @Test void anyAdminCanDecide() {
        service.decide(10L, "r@x.com", "ADMIN", "CONFIRM_URGENT", "ok");
        verify(complaintRepository).decideIfPending(eq(10L), eq(UrgencyStatus.CONFIRMED_URGENT), eq(admin),
                any(), eq("ok"), eq(Priority.URGENT));
    }
    @Test void firstDecisionWins() {
        when(complaintRepository.decideIfPending(anyLong(), any(), any(), any(), any(), any())).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.decide(10L, "a@x.com", "OFFICIAL", "CONFIRM_URGENT", null));
        verify(historyRepository, never()).save(any());
    }
    @Test void lockedDecisionCannotBeDecidedAgain() {
        complaint.setUrgencyStatus(UrgencyStatus.CONFIRMED_URGENT);
        assertThrows(RuntimeException.class, () -> service.decide(10L, "r@x.com", "ADMIN", "MARK_NORMAL", "long enough reason"));
        verify(complaintRepository, never()).decideIfPending(anyLong(), any(), any(), any(), any(), any());
    }
    @Test void normalComplaintCannotBeDecided() {
        complaint.setUrgencyStatus(UrgencyStatus.NONE);
        assertThrows(RuntimeException.class, () -> service.decide(10L, "a@x.com", "OFFICIAL", "CONFIRM_URGENT", null));
    }
    @Test void unknownDecisionRejected() {
        assertThrows(RuntimeException.class, () -> service.decide(10L, "a@x.com", "OFFICIAL", "MAYBE", null));
    }

    // ---- admin override
    @Test void overrideChangesLockedDecisionKeepsHistoryAndNotifiesCitizen() {
        complaint.setUrgencyStatus(UrgencyStatus.DOWNGRADED_TO_NORMAL);
        complaint.setPriority(Priority.MEDIUM);
        complaint.setReminderCount(2);
        service.overrideDecision(10L, "r@x.com", "CONFIRM_URGENT", "Citizen confirmed by phone later");
        assertEquals(UrgencyStatus.CONFIRMED_URGENT, complaint.getUrgencyStatus());
        assertEquals(Priority.URGENT, complaint.getPriority());
        assertTrue(complaint.isWasInitiallyUrgent());
        assertTrue(complaint.isUrgencyOverridden());
        assertEquals("x".repeat(25), complaint.getUrgencyReason());
        assertEquals(2, complaint.getReminderCount()); // override never resets reminders
        verify(notificationService).notify(eq(citizen), anyString(), eq(10L));
        verify(historyRepository).save(any(ComplaintHistory.class));
    }
    @Test void overrideNeedsNoteAdminAndLockedState() {
        complaint.setUrgencyStatus(UrgencyStatus.CONFIRMED_URGENT);
        assertThrows(RuntimeException.class, () -> service.overrideDecision(10L, "r@x.com", "MARK_NORMAL", "short"));
        assertThrows(RuntimeException.class, () -> service.overrideDecision(10L, "a@x.com", "MARK_NORMAL", "long enough reason"));
        assertThrows(RuntimeException.class, () -> service.overrideDecision(10L, "r@x.com", "CONFIRM_URGENT", "long enough reason")); // same state
        complaint.setUrgencyStatus(UrgencyStatus.PENDING);
        assertThrows(RuntimeException.class, () -> service.overrideDecision(10L, "r@x.com", "MARK_NORMAL", "long enough reason"));
    }

    // ---- priority lock
    @Test void priorityLockedForEveryUrgentFlowState() {
        for (UrgencyStatus s : new UrgencyStatus[]{UrgencyStatus.PENDING, UrgencyStatus.CONFIRMED_URGENT, UrgencyStatus.DOWNGRADED_TO_NORMAL}) {
            complaint.setUrgencyStatus(s);
            assertThrows(RuntimeException.class, () -> service.assertPriorityChangeAllowed(complaint, Priority.LOW));
        }
    }
    @Test void normalComplaintAllowsLowMediumHighButNotUrgent() {
        complaint.setUrgencyStatus(UrgencyStatus.NONE);
        assertDoesNotThrow(() -> service.assertPriorityChangeAllowed(complaint, Priority.LOW));
        assertDoesNotThrow(() -> service.assertPriorityChangeAllowed(complaint, Priority.MEDIUM));
        assertDoesNotThrow(() -> service.assertPriorityChangeAllowed(complaint, Priority.HIGH));
        assertThrows(RuntimeException.class, () -> service.assertPriorityChangeAllowed(complaint, Priority.URGENT));
    }

    // ---- visibility
    @Test void phoneOnlyForAdminAndAssignedOfficial() {
        ComplaintDto.Response r1 = new ComplaintDto.Response();
        service.decorateForCaller(r1, complaint, assigned, "OFFICIAL");
        assertEquals("9876543210", r1.getCitizenPhone());
        assertTrue(r1.getCanDecideUrgency());

        ComplaintDto.Response r2 = new ComplaintDto.Response();
        service.decorateForCaller(r2, complaint, admin, "ADMIN");
        assertEquals("9876543210", r2.getCitizenPhone());

        ComplaintDto.Response r3 = new ComplaintDto.Response();
        service.decorateForCaller(r3, complaint, otherOfficial, "OFFICIAL");
        assertNull(r3.getCitizenPhone());
        assertFalse(r3.getCanDecideUrgency());

        ComplaintDto.Response r4 = new ComplaintDto.Response();
        service.decorateForCaller(r4, complaint, citizen, "CITIZEN");
        assertNull(r4.getCitizenPhone());
    }
    @Test void normalComplaintNeverExposesPhone() {
        complaint.setUrgencyStatus(UrgencyStatus.NONE);
        ComplaintDto.Response r = new ComplaintDto.Response();
        service.decorateForCaller(r, complaint, admin, "ADMIN");
        assertNull(r.getCitizenPhone());
    }
    @Test void trackLabelsAndHistoryFilter() {
        assertEquals("Urgent (pending verification)", service.trackLabel(complaint));
        complaint.setUrgencyStatus(UrgencyStatus.CONFIRMED_URGENT);
        assertEquals("Urgent (verified)", service.trackLabel(complaint));
        complaint.setUrgencyStatus(UrgencyStatus.DOWNGRADED_TO_NORMAL);
        assertNull(service.trackLabel(complaint));
        assertTrue(UrgencyService.isUrgencyHistory("Urgency: verified as urgent"));
        assertFalse(UrgencyService.isUrgencyHistory("Complaint submitted by citizen"));
    }
}
