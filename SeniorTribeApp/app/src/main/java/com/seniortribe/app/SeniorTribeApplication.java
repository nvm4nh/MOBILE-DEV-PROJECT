package com.seniortribe.app;

import android.app.Application;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.seniortribe.app.auth.AuthRepository;
import com.seniortribe.app.auth.GoogleCredentialSession;

public class SeniorTribeApplication extends Application {
    private AuthRepository authRepository;
    private GoogleCredentialSession googleCredentialSession;
    private String authorizedUid;
    private String previousSessionUid;

    @Override
    public void onCreate() {
        super.onCreate();
        if (FirebaseApp.initializeApp(this) == null) {
            throw new IllegalStateException("Firebase configuration is missing");
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        // Supported by the shared project's Firebase BoM 32.8.0.
        // Private profiles and membership results stay in memory only.
        db.setFirestoreSettings(new FirebaseFirestoreSettings.Builder(db.getFirestoreSettings())
                .setPersistenceEnabled(false).build());
        FirebaseAuth auth = FirebaseAuth.getInstance();
        googleCredentialSession = new GoogleCredentialSession(this);
        authRepository = new AuthRepository(auth, db, googleCredentialSession);
        FirebaseUser initial = auth.getCurrentUser();
        previousSessionUid = initial == null ? null : initial.getUid();
        auth.addAuthStateListener(changed -> {
            FirebaseUser user = changed.getCurrentUser();
            if (user == null || !user.getUid().equals(authorizedUid)) authorizedUid = null;
            // Also cover sign-out initiated by the shared ProfileFragment.
            if (user == null && previousSessionUid != null) googleCredentialSession.clearState();
            previousSessionUid = user == null ? null : user.getUid();
        });
    }

    public AuthRepository getAuthRepository() { return authRepository; }
    public GoogleCredentialSession getGoogleCredentialSession() { return googleCredentialSession; }

    public void markActiveAccess(String uid) {
        if (!authRepository.isCurrentUser(uid)) throw new IllegalStateException("SESSION");
        authorizedUid = uid;
    }

    public void clearActiveAccess() { authorizedUid = null; }

    public boolean hasActiveAccess() {
        return authorizedUid != null && authRepository.isCurrentUser(authorizedUid);
    }
}
