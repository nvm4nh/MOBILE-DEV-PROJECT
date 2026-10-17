package com.seniortribe.app.auth;

import java.util.List;

/** Pure validation of a server membership; malformed active data cannot grant access. */
public final class MembershipPolicy {
    private MembershipPolicy() { }

    public static boolean hasActiveRoles(Object active, Object value) {
        if (!(active instanceof Boolean)) throw new IllegalStateException("MEMBERSHIP");
        if (!Boolean.TRUE.equals(active)) return false;
        if (!(value instanceof List) || ((List<?>) value).isEmpty()) {
            throw new IllegalStateException("MEMBERSHIP");
        }
        for (Object role : (List<?>) value) {
            if (!OrganizationRole.isKnown(role)) throw new IllegalStateException("MEMBERSHIP");
        }
        return true;
    }
}
