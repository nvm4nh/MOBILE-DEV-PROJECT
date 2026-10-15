package com.seniortribe.app.auth;

public final class UserProfile {

    public final String uid;
    public final String displayName;
    public final String email;
    public final String area;

    public UserProfile(
            String uid,
            String displayName,
            String email,
            String area
    ) {
        this.uid = uid;
        this.displayName = displayName;
        this.email = email;
        this.area = area;
    }
}