package com.seniortribe.app.auth;

import com.seniortribe.app.models.AccountOrganization;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class ProfilePolicyTest {
    private static Map<String, Object> membership(boolean active, Object roles) {
        Map<String, Object> data = new HashMap<>();
        data.put("active", active); data.put("roles", roles); data.put("orgName", "Tổ chức A");
        return data;
    }
    private static AccountOrganization member() {
        return AccountOrganization.fromMembership("org-a", membership(true, Arrays.asList("RECORDER", "REVIEWER")));
    }
    @Test public void validNameAndOptionalArea() {
        assertEquals(ProfileInput.Problem.NONE, ProfileInput.profile(" Nguyễn Văn A ", ""));
    }
    @Test public void areaLimitAndNoMultiline() {
        assertEquals(ProfileInput.Problem.NONE, ProfileInput.profile("An", String.join("", Collections.nCopies(120, "a"))));
        assertEquals(ProfileInput.Problem.AREA, ProfileInput.profile("An", String.join("", Collections.nCopies(121, "a"))));
        assertEquals(ProfileInput.Problem.AREA, ProfileInput.profile("An", "Q1\nQ2"));
    }
    @Test public void nameLimitAndNoMultiline() {
        assertEquals(ProfileInput.Problem.NAME, ProfileInput.profile("A", ""));
        assertEquals(ProfileInput.Problem.NAME, ProfileInput.profile("An\nBình", ""));
        assertEquals(ProfileInput.Problem.NAME, ProfileInput.profile(String.join("", Collections.nCopies(81, "a")), ""));
    }
    @Test public void passwordsAreNotTrimmed() {
        assertEquals(ProfileInput.Problem.NONE, ProfileInput.password("old pass", " newpass ", " newpass "));
        assertEquals(ProfileInput.Problem.PASSWORD_MATCH, ProfileInput.password("old pass", " newpass ", "newpass"));
    }
    @Test public void changeRequiresCurrentPassword() {
        assertEquals(ProfileInput.Problem.CURRENT_PASSWORD, ProfileInput.password("", "newpass1", "newpass1"));
    }
    @Test public void changeRejectsShortOrSamePassword() {
        assertEquals(ProfileInput.Problem.NEW_PASSWORD, ProfileInput.password("oldpass1", "short", "short"));
        assertEquals(ProfileInput.Problem.PASSWORD_UNCHANGED, ProfileInput.password("oldpass1", "oldpass1", "oldpass1"));
    }
    @Test public void allAssignedRolesAreRetainedWithoutDuplicates() {
        AccountOrganization org = AccountOrganization.fromMembership("org-a", membership(true,
                Arrays.asList("RECORDER", "REPRESENTATIVE", "RECORDER")));
        assertTrue(org.active);
        assertEquals(Arrays.asList("RECORDER", "REPRESENTATIVE"), org.roles);
    }
    @Test public void inactiveMembershipDoesNotBecomeActiveFromVerifiedOrganization() {
        AccountOrganization org = AccountOrganization.fromMembership("org-a", membership(false, Arrays.asList("REVIEWER")));
        Map<String, Object> details = new HashMap<>(); details.put("verified", true);
        org = org.withDetails(details);
        assertFalse(org.active);
        assertEquals(AccountOrganization.Verification.VERIFIED, org.verification);
    }
    @Test(expected=IllegalStateException.class) public void unknownRoleCannotBePresentedAsAValidGrant() {
        AccountOrganization.fromMembership("org-a", membership(true, Arrays.asList("ADMIN")));
    }
    @Test(expected=IllegalStateException.class) public void activeMembershipWithoutRolesFails() {
        AccountOrganization.fromMembership("org-a", membership(true, Collections.emptyList()));
    }
    @Test(expected=IllegalStateException.class) public void stringActiveFlagIsNotTrusted() {
        Map<String, Object> data = membership(true, Arrays.asList("RECORDER")); data.put("active", "true");
        AccountOrganization.fromMembership("org-a", data);
    }
    @Test public void membershipCannotSupplyVerificationBadge() {
        Map<String, Object> data = membership(true, Arrays.asList("RECORDER")); data.put("verified", true);
        assertEquals(AccountOrganization.Verification.UNKNOWN, AccountOrganization.fromMembership("org-a", data).verification);
    }
    @Test public void missingAndUnreadableOrganizationAreUnknownNotUnverified() {
        assertEquals(AccountOrganization.Verification.UNKNOWN, member().withStatus(AccountOrganization.Details.UNAVAILABLE).verification);
        assertEquals(AccountOrganization.Verification.UNKNOWN, member().withDetails(null).verification);
        assertEquals(AccountOrganization.Verification.UNVERIFIED, member().withDetails(Collections.singletonMap("verified", false)).verification);
    }
    @Test(expected=IllegalStateException.class) public void stringVerificationFlagIsNotTrusted() {
        member().withDetails(Collections.singletonMap("verified", "true"));
    }
    @Test(expected=IllegalStateException.class) public void mismatchedOrganizationIdIsRejected() {
        member().withDetails(Collections.singletonMap("orgId", "org-other"));
    }
    @Test public void rolesCannotBeChangedByTheUi() {
        try { member().roles.add("REPRESENTATIVE"); fail("must be immutable"); }
        catch (UnsupportedOperationException expected) { }
    }
}
