package com.project.complaint.service;

import com.project.complaint.entity.Role;
import com.project.complaint.entity.User;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertEmailServiceTest {

    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher events;

    /** Mutable test clock. */
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    TestClock clock;
    AlertEmailService service;
    User official, citizen;

    @BeforeEach
    void setUp() {
        clock = new TestClock();
        service = new AlertEmailService(userRepository, events, clock);
        official = User.builder().id(1L).name("Asha").email("a@x.com").role(Role.OFFICIAL).build();
        citizen = User.builder().id(2L).name("Cit").email("c@x.com").role(Role.CITIZEN).build();
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(official));
        when(userRepository.findByEmail("c@x.com")).thenReturn(Optional.of(citizen));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test void citizensCannotSetAlertEmail() {
        assertThrows(RuntimeException.class, () -> service.set("c@x.com", "me@example.com"));
    }

    @Test void invalidAddressRejected() {
        assertThrows(RuntimeException.class, () -> service.set("a@x.com", "not-an-email"));
        assertThrows(RuntimeException.class, () -> service.set("a@x.com", "a@b"));
        assertNull(official.getAlertEmail());
    }

    @Test void setStoresUnverifiedAddressHashedTokenAndPublishesRawToken() {
        service.set("a@x.com", "  Alerts@Example.com ");
        assertEquals("alerts@example.com", official.getAlertEmail());
        assertFalse(official.isAlertEmailVerified());
        assertNotNull(official.getAlertEmailTokenHash());
        assertEquals(clock.now.atOffset(ZoneOffset.UTC).toLocalDateTime().plusHours(24), official.getAlertEmailTokenExpiresAt());

        ArgumentCaptor<AlertEmailVerificationRequested> ev = ArgumentCaptor.forClass(AlertEmailVerificationRequested.class);
        verify(events).publishEvent(ev.capture());
        String raw = ev.getValue().rawToken();
        assertNotEquals(raw, official.getAlertEmailTokenHash()); // only the hash is stored
        assertEquals(AlertEmailService.sha256Hex(raw), official.getAlertEmailTokenHash());
    }

    @Test void unverifiedOrUnsetAddressIsTreatedAsUnset() {
        assertNull(AlertEmailService.verifiedAlertEmail(official));
        official.setAlertEmail("x@example.com");
        assertNull(AlertEmailService.verifiedAlertEmail(official)); // set but unverified
        official.setAlertEmailVerified(true);
        assertEquals("x@example.com", AlertEmailService.verifiedAlertEmail(official));
        official.setAlertEmail(" ");
        assertNull(AlertEmailService.verifiedAlertEmail(official));
    }

    @Test void verifyIsSingleUseAndMarksVerified() {
        service.set("a@x.com", "x@example.com");
        when(userRepository.findByAlertEmailTokenHash(anyString())).thenReturn(Optional.of(official));
        service.verify("any-token");
        assertTrue(official.isAlertEmailVerified());
        assertNull(official.getAlertEmailTokenHash());
        // a second use finds no hash any more
        when(userRepository.findByAlertEmailTokenHash(anyString())).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.verify("any-token"));
    }

    @Test void expiredTokenRejected() {
        service.set("a@x.com", "x@example.com");
        when(userRepository.findByAlertEmailTokenHash(anyString())).thenReturn(Optional.of(official));
        clock.now = clock.now.plusSeconds(24 * 3600 + 1);
        assertThrows(RuntimeException.class, () -> service.verify("t"));
        assertFalse(official.isAlertEmailVerified());
    }

    @Test void changingAddressResetsVerification() {
        official.setAlertEmail("old@example.com");
        official.setAlertEmailVerified(true);
        service.set("a@x.com", "new@example.com");
        assertEquals("new@example.com", official.getAlertEmail());
        assertFalse(official.isAlertEmailVerified());
    }

    @Test void resendIsRateLimited() {
        service.set("a@x.com", "x@example.com");
        assertThrows(RuntimeException.class, () -> service.resend("a@x.com"));
        clock.now = clock.now.plusSeconds(61);
        assertDoesNotThrow(() -> service.resend("a@x.com"));
    }

    @Test void clearRemovesAddressAndToken() {
        service.set("a@x.com", "x@example.com");
        service.clear("a@x.com");
        assertNull(official.getAlertEmail());
        assertNull(official.getAlertEmailTokenHash());
        assertFalse(official.isAlertEmailVerified());
    }
}
