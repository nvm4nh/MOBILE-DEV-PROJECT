package com.seniortribe.app.auth;

public final class ProfileInput {

    public enum Problem {
        NONE,
        NAME,
        AREA,
        PHONE,
        CURRENT_PASSWORD,
        NEW_PASSWORD,
        PASSWORD_MATCH,
        PASSWORD_UNCHANGED
    }

    private ProfileInput() { }

    public static String phone(String value) {
        return value == null ? "" : value.trim();
    }

    public static Problem profile(String name, String area) {
        return profile(name, area, "");
    }

    public static Problem profile(
            String name, String area, String phone) {

        if (!InputValidator.validName(name)) {
            return Problem.NAME;
        }

        if (area == null
                || area.trim().length() > 120
                || area.contains("\n")
                || area.contains("\r")) {
            return Problem.AREA;
        }

        String cleanPhone = phone(phone);

        if (!cleanPhone.isEmpty()
                && !cleanPhone.matches("[+]?[0-9]{8,15}")) {
            return Problem.PHONE;
        }

        return Problem.NONE;
    }

    public static Problem field(String field, String value) {
        if ("displayName".equals(field)) {
            return profile(value, "", "");
        }

        if ("area".equals(field)) {
            return profile("Thành viên", value, "");
        }

        if ("phone".equals(field)) {
            return profile("Thành viên", "", value);
        }

        return Problem.NAME;
    }

    public static Problem password(
            String current, String next, String repeated) {

        if (current == null || current.isEmpty()) {
            return Problem.CURRENT_PASSWORD;
        }

        if (next == null || next.length() < 8) {
            return Problem.NEW_PASSWORD;
        }

        if (!next.equals(repeated)) {
            return Problem.PASSWORD_MATCH;
        }

        if (next.equals(current)) {
            return Problem.PASSWORD_UNCHANGED;
        }

        return Problem.NONE;
    }
}