package com.seniortribe.app.auth;

/** Shared validation for the F03 form and repository. Password whitespace is significant. */
public final class ProfileInput {
    public enum Problem { NONE, NAME, AREA, CURRENT_PASSWORD, NEW_PASSWORD, PASSWORD_MATCH, PASSWORD_UNCHANGED }
    private ProfileInput() { }

    public static Problem profile(String name, String area) {
        if (!InputValidator.validName(name)) return Problem.NAME;
        if (area == null || area.trim().length() > 120 || area.contains("\n") || area.contains("\r")) {
            return Problem.AREA;
        }
        return Problem.NONE;
    }

    public static Problem password(String current, String next, String repeated) {
        if (current == null || current.isEmpty()) return Problem.CURRENT_PASSWORD;
        if (next == null || next.length() < 8) return Problem.NEW_PASSWORD;
        if (!next.equals(repeated)) return Problem.PASSWORD_MATCH;
        if (next.equals(current)) return Problem.PASSWORD_UNCHANGED;
        return Problem.NONE;
    }
}
