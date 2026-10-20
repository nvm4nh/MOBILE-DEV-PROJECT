package com.seniortribe.app.auth;

import androidx.credentials.exceptions.GetCredentialProviderConfigurationException;
import androidx.credentials.exceptions.GetCredentialUnsupportedException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.seniortribe.app.R;

public final class AuthErrors {

    private AuthErrors() { }

    public static int message(Throwable error) {
        if (error != null
                && "EMAIL_NOT_VERIFIED".equals(error.getMessage())) {
            return R.string.st_email_unverified;
        }

        if (error instanceof FirebaseNetworkException) {
            return R.string.st_auth_error_network;
        }

        if (error instanceof FirebaseTooManyRequestsException) {
            return R.string.st_auth_error_rate_limit;
        }

        if (error instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) error).getCode()) {
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    return R.string.st_auth_error_network;

                case PERMISSION_DENIED:
                    return R.string.st_auth_error_permission;

                case UNAUTHENTICATED:
                    return R.string.st_auth_error_session;

                default:
                    break;
            }
        }

        if (error instanceof FirebaseAuthException) {
            switch (((FirebaseAuthException) error).getErrorCode()) {
                case "ERROR_INVALID_CREDENTIAL":
                case "ERROR_INVALID_LOGIN_CREDENTIALS":
                case "ERROR_WRONG_PASSWORD":
                case "ERROR_USER_NOT_FOUND":
                    return R.string.st_login_error_credentials;

                case "ERROR_USER_DISABLED":
                    return R.string.st_auth_error_disabled;

                case "ERROR_INVALID_EMAIL":
                    return R.string.st_auth_error_email;

                case "ERROR_TOO_MANY_REQUESTS":
                    return R.string.st_auth_error_rate_limit;

                case "ERROR_USER_TOKEN_EXPIRED":
                case "ERROR_INVALID_USER_TOKEN":
                case "ERROR_REQUIRES_RECENT_LOGIN":
                    return R.string.st_auth_error_session;

                default:
                    break;
            }
        }

        if (error != null) {
            String reason = error.getMessage();

            if ("SESSION".equals(reason)) {
                return R.string.st_auth_error_session;
            }

            if ("PROFILE".equals(reason)
                    || "PROFILE_INPUT".equals(reason)
                    || "PROFILE_FIELD".equals(reason)) {
                return R.string.st_auth_error_profile;
            }

            if ("MEMBERSHIP".equals(reason)) {
                return R.string.st_access_error_membership;
            }
        }

        return R.string.st_auth_error_generic;
    }

    public static int googleMessage(Throwable error) {
        if (error instanceof NoCredentialException) {
            return R.string.st_google_error_no_account;
        }

        if (error instanceof GetCredentialProviderConfigurationException
                || error instanceof GetCredentialUnsupportedException) {
            return R.string.st_google_error_unavailable;
        }

        if (error instanceof FirebaseAuthException) {
            switch (((FirebaseAuthException) error).getErrorCode()) {
                case "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL":
                case "ERROR_CREDENTIAL_ALREADY_IN_USE":
                case "ERROR_EMAIL_ALREADY_IN_USE":
                    return R.string.st_google_error_existing_account;

                case "ERROR_INVALID_CREDENTIAL":
                case "ERROR_INVALID_LOGIN_CREDENTIALS":
                case "ERROR_OPERATION_NOT_ALLOWED":
                    return R.string.st_google_error_generic;

                default:
                    return message(error);
            }
        }

        if (error != null
                && "GOOGLE_CONFIG".equals(error.getMessage())) {
            return R.string.st_google_error_config;
        }

        if (error instanceof FirebaseNetworkException
                || error instanceof FirebaseTooManyRequestsException) {
            return message(error);
        }

        return R.string.st_google_error_generic;
    }
}