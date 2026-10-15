package com.seniortribe.app.auth;

public enum OrganizationRole {

    RECORDER,
    REVIEWER,
    REPRESENTATIVE;

    public static boolean isKnown(Object value) {
        if (!(value instanceof String)) {
            return false;
        }

        for (OrganizationRole role : values()) {
            if (role.name().equals(value)) {
                return true;
            }
        }

        return false;
    }
}