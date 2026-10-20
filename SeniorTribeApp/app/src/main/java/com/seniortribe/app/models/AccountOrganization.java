package com.seniortribe.app.models;

import com.seniortribe.app.auth.MembershipPolicy;
import com.seniortribe.app.auth.OrganizationRole;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Read-only F03 projection. Never serialized to Firestore or used to assign a role. */
public final class AccountOrganization {
    public enum Details { LOADED, UNAVAILABLE, MISSING, INVALID }
    public enum Verification { VERIFIED, UNVERIFIED, UNKNOWN }
    public final String id;
    public final String name;
    public final String area;
    public final String address;
    public final String phone;
    public final boolean active;
    public final List<String> roles;
    public final Details details;
    public final Verification verification;

    private AccountOrganization(String id, String name, String area, String address, String phone,
                                boolean active, List<String> roles, Details details, Verification verification) {
        this.id = id;
        this.name = name;
        this.area = area;
        this.address = address;
        this.phone = phone;
        this.active = active;
        this.roles = Collections.unmodifiableList(new ArrayList<>(roles));
        this.details = details;
        this.verification = verification;
    }

    public static AccountOrganization fromMembership(String id, Map<String, Object> data) {
        if (id == null || id.isEmpty() || data == null) throw new IllegalStateException("MEMBERSHIP");
        Object active = data.get("active");
        boolean assigned = MembershipPolicy.hasActiveRoles(active, data.get("roles"));
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        Object values = data.get("roles");
        if (values instanceof List) {
            for (Object value : (List<?>) values) {
                if (!OrganizationRole.isKnown(value)) throw new IllegalStateException("MEMBERSHIP");
                roles.add((String) value);
            }
        } else if (values != null) throw new IllegalStateException("MEMBERSHIP");
        Object name = data.get("orgName");
        if (name != null && !(name instanceof String)) throw new IllegalStateException("MEMBERSHIP");
        String title = name == null || ((String) name).trim().isEmpty() ? id : ((String) name).trim();
        return new AccountOrganization(id, title, "", "", "", assigned,
                new ArrayList<>(roles), Details.UNAVAILABLE, Verification.UNKNOWN);
    }

    public AccountOrganization withDetails(Map<String, Object> data) {
        if (data == null) return withStatus(Details.MISSING);
        String storedId = text(data, "orgId");
        if (!storedId.isEmpty() && !id.equals(storedId)) throw new IllegalStateException("ORGANIZATION");
        String title = text(data, "name");
        Object value = data.get("verified");
        if (value != null && !(value instanceof Boolean)) throw new IllegalStateException("ORGANIZATION");
        Verification verified = value == null ? Verification.UNKNOWN
                : Boolean.TRUE.equals(value) ? Verification.VERIFIED : Verification.UNVERIFIED;
        return new AccountOrganization(id, title.isEmpty() ? name : title,
                text(data, "area"), text(data, "address"), text(data, "phone"), active,
                roles, Details.LOADED, verified);
    }

    public AccountOrganization withStatus(Details value) {
        return new AccountOrganization(id, name, "", "", "", active, roles, value, Verification.UNKNOWN);
    }

    private static String text(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return "";
        if (!(value instanceof String)) throw new IllegalStateException("ORGANIZATION");
        return ((String) value).trim();
    }
}
