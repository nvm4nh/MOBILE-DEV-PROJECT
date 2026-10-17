package com.seniortribe.app.auth;

import com.google.firebase.auth.FirebaseAuthException;
import com.seniortribe.app.R;

public final class ProfileErrors {
    private ProfileErrors() { }
    public static int password(Throwable error) {
        if (error instanceof FirebaseAuthException) {
            switch (((FirebaseAuthException) error).getErrorCode()) {
                case "ERROR_WRONG_PASSWORD":
                case "ERROR_INVALID_CREDENTIAL":
                case "ERROR_INVALID_LOGIN_CREDENTIALS": return R.string.st_profile_password_incorrect;
                case "ERROR_WEAK_PASSWORD": return R.string.st_profile_password_policy;
                case "ERROR_REQUIRES_RECENT_LOGIN": return R.string.st_profile_password_recent;
                default: break;
            }
        }
        if (error != null && "PASSWORD_PROVIDER".equals(error.getMessage())) {
            return R.string.st_profile_google_password_note;
        }
        return AuthErrors.message(error);
    }

    public static int input(ProfileInput.Problem problem) {
        switch (problem) {
            case NAME: return R.string.st_profile_name_error;
            case AREA: return R.string.st_profile_area_error;
            case CURRENT_PASSWORD: return R.string.st_profile_current_password_error;
            case NEW_PASSWORD: return R.string.st_profile_new_password_error;
            case PASSWORD_MATCH: return R.string.st_profile_repeat_password_error;
            case PASSWORD_UNCHANGED: return R.string.st_profile_password_unchanged;
            default: return 0;
        }
    }
}
