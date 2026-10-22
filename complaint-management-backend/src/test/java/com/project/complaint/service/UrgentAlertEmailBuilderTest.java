package com.project.complaint.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UrgentAlertEmailBuilderTest {

    private UrgentAlertEmailBuilder.Data data(String assignedTo, int n, long hours) {
        return new UrgentAlertEmailBuilder.Data(7L, "CMP-2026-000007", "Burst pipe", "Water Supply", "12 Main Road",
                LocalDateTime.of(2026, 1, 1, 9, 30), "Water everywhere near <school>", "Risk of accidents", assignedTo, n, 4, hours);
    }

    @Test void linkIsRoleSpecificAndCarriesNoToken() {
        assertEquals("http://app/official/complaint/7", UrgentAlertEmailBuilder.link("http://app", false, 7L));
        assertEquals("http://app/admin/complaint/7", UrgentAlertEmailBuilder.link("http://app/", true, 7L));
    }

    @Test void initialEmailHasRequiredContentAndNeverAPhoneNumber() {
        EmailMessage m = UrgentAlertEmailBuilder.build("o@example.com", "Asha", false,
                UrgentAlertRequested.Kind.INITIAL, data("Asha", 0, 0), "http://app");
        assertEquals("o@example.com", m.to());
        assertTrue(m.subject().contains("CMP-2026-000007") && m.subject().contains("URGENT"));
        for (String part : new String[]{"CMP-2026-000007", "Burst pipe", "Water Supply", "12 Main Road", "Risk of accidents",
                "Asha", "http://app/official/complaint/7", "call the citizen"}) {
            assertTrue(m.textBody().contains(part), part);
        }
        assertFalse(m.textBody().toLowerCase().contains("token"));
        assertFalse(m.textBody().contains("tel:"));
        assertTrue(m.htmlBody().contains("&lt;school&gt;"), "HTML must be escaped");
    }

    @Test void reminderMentionsNumberHoursAndAssignee() {
        EmailMessage m = UrgentAlertEmailBuilder.build("o@example.com", "Asha", false,
                UrgentAlertRequested.Kind.REMINDER, data("Asha", 2, 12), "http://app");
        assertTrue(m.subject().contains("Reminder 2 of 4"));
        assertTrue(m.textBody().contains("12 hours"));
        assertTrue(m.textBody().contains("Assigned to: Asha"));
    }

    @Test void escalationToAdminStatesFourRemindersOver24Hours() {
        EmailMessage m = UrgentAlertEmailBuilder.build("a@example.com", "Root", true,
                UrgentAlertRequested.Kind.ESCALATION, data("Asha", 4, 24), "http://app");
        assertTrue(m.textBody().contains("Asha was reminded 4 times over 24 hours"));
        assertTrue(m.textBody().contains("http://app/admin/complaint/7"));
    }

    @Test void unassignedAdminEmailAsksForAssignment() {
        EmailMessage m = UrgentAlertEmailBuilder.build("a@example.com", "Root", true,
                UrgentAlertRequested.Kind.INITIAL, data(null, 0, 0), "http://app");
        assertTrue(m.textBody().contains("Not assigned to an official yet"));
        assertTrue(m.textBody().contains("assign it"));
    }
}
