package com.seniortribe.app.auth;

import java.util.regex.Pattern;

public final class InputValidator {

    public enum Problem {
        NONE,
        NAME,
        EMAIL,
        PASSWORD_REQUIRED,
        PASSWORD_LENGTH,
        PASSWORD_MATCH
    }

    private static final Pattern EMAIL =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private InputValidator() {
    }

    public static String normalizeEmail(String value) {
        return value == null ? "" : value.trim();
    }

    public static boolean validEmail(String value) {
        String email = normalizeEmail(value);

        return email.length() <= 254
                && EMAIL.matcher(email).matches();
    }

    public static boolean validName(String value) {
        if (value == null) {
            return false;
        }

        String name = value.trim();

        return name.length() >= 2
                && name.length() <= 80
                && !name.contains("\n")
                && !name.contains("\r");
    }

    public static Problem registration(
            String name,
            String email,
            String password,
            String repeated
    ) {
        if (!validName(name)) {
            return Problem.NAME;
        }

        if (!validEmail(email)) {
            return Problem.EMAIL;
        }

        if (password == null || password.isEmpty()) {
            return Problem.PASSWORD_REQUIRED;
        }

        if (password.length() < 8) {
            return Problem.PASSWORD_LENGTH;
        }

        // Khoang trang van la mot phan cua mat khau.
        if (!password.equals(repeated)) {
            return Problem.PASSWORD_MATCH;
        }

        return Problem.NONE;
    }

    public static Problem login(String email, String password) {
        if (!validEmail(email)) {
            return Problem.EMAIL;
        }

        if (password == null || password.isEmpty()) {
            return Problem.PASSWORD_REQUIRED;
        }

        return Problem.NONE;
    }
}