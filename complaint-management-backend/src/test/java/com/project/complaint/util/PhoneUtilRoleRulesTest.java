package com.project.complaint.util;

import com.project.complaint.entity.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneUtilRoleRulesTest {

    @Test void citizenMustHaveAValidNumber() {
        assertThrows(IllegalStateException.class, () -> PhoneUtil.normalizeForRole(Role.CITIZEN, null));
        assertThrows(IllegalStateException.class, () -> PhoneUtil.normalizeForRole(Role.CITIZEN, "  "));
        assertThrows(IllegalStateException.class, () -> PhoneUtil.normalizeForRole(Role.CITIZEN, "12345"));
        assertEquals("9876543210", PhoneUtil.normalizeForRole(Role.CITIZEN, "+91 98765-43210"));
    }

    @Test void officialAndAdminMayLeaveItEmpty() {
        for (Role r : new Role[]{Role.OFFICIAL, Role.ADMIN}) {
            assertNull(PhoneUtil.normalizeForRole(r, null));
            assertNull(PhoneUtil.normalizeForRole(r, ""));
            assertNull(PhoneUtil.normalizeForRole(r, "   "));
        }
    }

    @Test void aNumberThatIsGivenMustStillBeValid() {
        assertThrows(IllegalStateException.class, () -> PhoneUtil.normalizeForRole(Role.OFFICIAL, "12345"));
        assertThrows(IllegalStateException.class, () -> PhoneUtil.normalizeForRole(Role.ADMIN, "abc"));
        assertEquals("9876543210", PhoneUtil.normalizeForRole(Role.OFFICIAL, "9876543210"));
    }

    @Test void uniquenessAppliesToCitizensOnly() {
        assertTrue(PhoneUtil.mustBeUnique(Role.CITIZEN));
        assertFalse(PhoneUtil.mustBeUnique(Role.OFFICIAL));
        assertFalse(PhoneUtil.mustBeUnique(Role.ADMIN));
    }
}
