package com.seniortribe.app.auth;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class AuthPolicyTest {
    @Test public void recorderMembershipGrantsAccess() {
        assertTrue(MembershipPolicy.hasActiveRoles(true, Arrays.asList("RECORDER")));
    }
    @Test public void multipleAssignedRolesGrantAccess() {
        assertTrue(MembershipPolicy.hasActiveRoles(true, Arrays.asList("REVIEWER", "REPRESENTATIVE")));
    }
    @Test public void inactiveMembershipDoesNotGrantAccess() {
        assertFalse(MembershipPolicy.hasActiveRoles(false, Collections.emptyList()));
    }
    @Test public void inactiveLegacyDataDoesNotGrantAccess() {
        assertFalse(MembershipPolicy.hasActiveRoles(false, "old data"));
    }
    @Test(expected = IllegalStateException.class) public void missingStatusFailsClosed() {
        MembershipPolicy.hasActiveRoles(null, Arrays.asList("RECORDER"));
    }
    @Test(expected = IllegalStateException.class) public void stringStatusFailsClosed() {
        MembershipPolicy.hasActiveRoles("true", Arrays.asList("RECORDER"));
    }
    @Test(expected = IllegalStateException.class) public void emptyAssignedRolesFailClosed() {
        MembershipPolicy.hasActiveRoles(true, Collections.emptyList());
    }
    @Test(expected = IllegalStateException.class) public void stringInsteadOfRoleListFailsClosed() {
        MembershipPolicy.hasActiveRoles(true, "RECORDER");
    }
    @Test(expected = IllegalStateException.class) public void unknownRoleAmongKnownRolesFailsClosed() {
        MembershipPolicy.hasActiveRoles(true, Arrays.asList("RECORDER", "ADMIN"));
    }
    @Test(expected = IllegalStateException.class) public void legacyUserRoleIsNotMembership() {
        MembershipPolicy.hasActiveRoles(true, Arrays.asList("Reporter"));
    }
    @Test(expected = IllegalStateException.class) public void roleCodesAreCaseSensitive() {
        MembershipPolicy.hasActiveRoles(true, Arrays.asList("recorder"));
    }
    @Test(expected = IllegalStateException.class) public void numericRoleFailsClosed() {
        MembershipPolicy.hasActiveRoles(true, Arrays.asList(7));
    }
    @Test public void nullRoleIsUnknown() { assertFalse(OrganizationRole.isKnown(null)); }
    @Test public void vietnameseNameIsAccepted() { assertTrue(InputValidator.validName("Nguyễn Văn Trường")); }
    @Test public void multilineNameIsRejected() { assertFalse(InputValidator.validName("A\nB")); }
    @Test public void incompleteEmailIsRejected() { assertFalse(InputValidator.validEmail("truong@")); }
    @Test public void existingShortPasswordCanBeUsedForLogin() {
        assertEquals(InputValidator.Problem.NONE, InputValidator.login("truong@example.com", "short"));
    }
    @Test public void newRegistrationRequiresMinimumLength() {
        assertEquals(InputValidator.Problem.PASSWORD_LENGTH,
                InputValidator.registration("Trường", "truong@example.com", "1234567", "1234567"));
    }
    @Test public void passwordSpacesArePreserved() {
        assertEquals(InputValidator.Problem.NONE,
                InputValidator.registration("Trường", "truong@example.com", "abc12345 ", "abc12345 "));
    }
    @Test public void passwordConfirmationIsExact() {
        assertEquals(InputValidator.Problem.PASSWORD_MATCH,
                InputValidator.registration("Trường", "truong@example.com", "abc12345 ", "abc12345"));
    }
}
