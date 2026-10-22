package com.project.complaint.service;

import com.project.complaint.config.UrgencyProperties;
import com.project.complaint.entity.*;
import com.project.complaint.repository.ComplaintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UrgencyReminderProcessorTest {

    static class TestClock extends Clock {
        Instant now;
        TestClock(Instant start) { this.now = start; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 10, 0);

    @Mock ComplaintRepository repo;
    @Mock UrgentAlertService alerts;

    TestClock clock;
    UrgencyProperties props;
    UrgencyReminderProcessor processor;
    Complaint c;
    User official;

    @BeforeEach
    void setUp() {
        clock = new TestClock(T0.toInstant(ZoneOffset.UTC));
        props = new UrgencyProperties(); // 6h interval, 5 min escalation, 4 reminders
        processor = new UrgencyReminderProcessor(repo, alerts, props, clock);
        official = User.builder().id(2L).name("Asha").role(Role.OFFICIAL).active(true).build();
        c = Complaint.builder().id(10L).complaintNumber("CMP-10").status(ComplaintStatus.ASSIGNED)
                .urgencyStatus(UrgencyStatus.PENDING).assignedOfficial(official)
                .urgencyAssignedAt(T0).createdAt(T0).build();

        when(repo.findById(10L)).thenAnswer(i -> Optional.of(c));
        // Conditional claims behave like the DB: they only succeed if the expected state still holds, and they mutate it.
        when(repo.claimReminder(eq(10L), anyInt(), anyInt(), any(), any(), any())).thenAnswer(i -> {
            Integer expected = i.getArgument(1);
            if (c.getReminderCount() != expected || c.getUrgencyStatus() != UrgencyStatus.PENDING) return 0;
            Integer next = i.getArgument(2);
            c.setReminderCount(next);
            c.setLastReminderAt(i.getArgument(3));
            return 1;
        });
        when(repo.claimEscalation(eq(10L), anyInt(), any(), any(), any())).thenAnswer(i -> {
            Integer expectedCount = i.getArgument(1);
            if (c.getAdminEscalatedAt() != null || c.getReminderCount() != expectedCount
                    || c.getUrgencyStatus() != UrgencyStatus.PENDING) return 0;
            c.setAdminEscalatedAt(i.getArgument(2));
            return 1;
        });
    }

    @Test void nothingBeforeSixHours() {
        clock.advance(Duration.ofHours(6).minusSeconds(1));
        assertFalse(processor.process(10L));
        verifyNoInteractions(alerts);
    }

    @Test void remindersAtSix_Twelve_Eighteen_TwentyFourHoursThenEscalationFiveMinutesAfterFourth() {
        for (int n = 1; n <= 4; n++) {
            clock.advance(n == 1 ? Duration.ofHours(6) : Duration.ofHours(6));
            assertTrue(processor.process(10L), "reminder " + n);
            verify(alerts).sendReminder(any(Complaint.class), eq(n), eq(6L * n));
            assertEquals(n, c.getReminderCount());
            assertFalse(processor.process(10L), "no duplicate for reminder " + n); // same instant: already claimed
        }
        clock.advance(Duration.ofMinutes(5).minusSeconds(1));
        assertFalse(processor.process(10L));
        verify(alerts, never()).sendEscalation(any(), anyLong());
        clock.advance(Duration.ofSeconds(1)); // exactly 5 minutes after the 4th reminder
        assertTrue(processor.process(10L));
        verify(alerts, times(1)).sendEscalation(any(Complaint.class), eq(24L));
        assertNotNull(c.getAdminEscalatedAt());
        // escalation happens exactly once
        clock.advance(Duration.ofHours(48));
        assertFalse(processor.process(10L));
        verify(alerts, times(1)).sendEscalation(any(), anyLong());
        verify(alerts, times(4)).sendReminder(any(), anyInt(), anyLong());
    }

    @Test void afterDowntimeOnlyOneReminderPerRunAndCatchUpIsSpreadOut() {
        clock.advance(Duration.ofHours(30)); // backend was down; everything is overdue
        assertTrue(processor.process(10L));
        verify(alerts).sendReminder(any(), eq(1), anyLong());
        assertFalse(processor.process(10L)); // next run, one minute later: no burst
        clock.advance(Duration.ofMinutes(1));
        assertFalse(processor.process(10L));
        verify(alerts, times(1)).sendReminder(any(), anyInt(), anyLong());
        clock.advance(Duration.ofHours(3)); // half an interval later the next catch-up is allowed
        assertTrue(processor.process(10L));
        verify(alerts).sendReminder(any(), eq(2), anyLong());
    }

    @Test void claimLostMeansNoSend() {
        clock.advance(Duration.ofHours(6));
        when(repo.claimReminder(anyLong(), anyInt(), anyInt(), any(), any(), any())).thenReturn(0); // another run/instance won
        assertFalse(processor.process(10L));
        verifyNoInteractions(alerts);
    }

    @Test void stopsWhenDecided() {
        clock.advance(Duration.ofHours(6));
        c.setUrgencyStatus(UrgencyStatus.CONFIRMED_URGENT);
        assertFalse(processor.process(10L));
        c.setUrgencyStatus(UrgencyStatus.DOWNGRADED_TO_NORMAL);
        assertFalse(processor.process(10L));
        verifyNoInteractions(alerts);
    }

    @Test void stopsOnTerminalStatuses() {
        clock.advance(Duration.ofHours(6));
        for (ComplaintStatus s : new ComplaintStatus[]{ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED, ComplaintStatus.REJECTED}) {
            c.setStatus(s);
            assertFalse(processor.process(10L), s.name());
        }
        verifyNoInteractions(alerts);
    }

    @Test void unassignedGetsFourRemindersButNoEscalation() {
        c.setAssignedOfficial(null);
        for (int n = 1; n <= 4; n++) {
            clock.advance(Duration.ofHours(6));
            assertTrue(processor.process(10L));
        }
        clock.advance(Duration.ofHours(1));
        assertFalse(processor.process(10L));
        verify(alerts, times(4)).sendReminder(any(), anyInt(), anyLong());
        verify(alerts, never()).sendEscalation(any(), anyLong());
    }

    @Test void reassignmentRestartsTheClockForTheNewOfficial() {
        clock.advance(Duration.ofHours(12));
        assertTrue(processor.process(10L)); // reminder 1 (catch-up), count = 1
        // admin reassigns now: the alert service resets the clock (simulated with the real reset logic)
        c.setReminderCount(0); c.setLastReminderAt(null); c.setAdminEscalatedAt(null);
        c.setUrgencyAssignedAt(LocalDateTime.now(clock));
        clock.advance(Duration.ofHours(5));
        assertFalse(processor.process(10L)); // new official: nothing before +6h
        clock.advance(Duration.ofHours(1));
        assertTrue(processor.process(10L));
        verify(alerts).sendReminder(any(), eq(1), eq(6L));
    }

    @Test void shortenedIntervalsFromConfigAreHonoured() {
        props.setReminderInterval(Duration.ofMinutes(1));
        props.setEscalationDelay(Duration.ofSeconds(10));
        clock.advance(Duration.ofMinutes(1));
        assertTrue(processor.process(10L));
    }
}
