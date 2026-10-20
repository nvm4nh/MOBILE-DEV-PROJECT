package com.seniortribe.app.models;

public final class ProfileUpdateResult {
    public final User profile;
    public final boolean authNameUpdated;
    public ProfileUpdateResult(User profile, boolean authNameUpdated) {
        this.profile = profile;
        this.authNameUpdated = authNameUpdated;
    }
}
