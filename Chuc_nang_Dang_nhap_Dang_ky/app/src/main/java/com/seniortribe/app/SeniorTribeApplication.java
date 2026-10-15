package com.seniortribe.app;

import android.app.Application;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.MemoryCacheSettings;
import com.seniortribe.app.auth.AuthRepository;

public final class SeniorTribeApplication extends Application {

    private AuthRepository authRepository;

    @Override
    public void onCreate() {
        super.onCreate();

        FirebaseApp firebaseApp = FirebaseApp.initializeApp(this);

        if (firebaseApp == null) {
            throw new IllegalStateException(
                    "Không thể khởi tạo Firebase. Kiểm tra google-services.json."
            );
        }

        FirebaseFirestore firestore =
                FirebaseFirestore.getInstance(firebaseApp);

        FirebaseFirestoreSettings settings =
                new FirebaseFirestoreSettings.Builder(
                        firestore.getFirestoreSettings()
                )
                        .setLocalCacheSettings(
                                MemoryCacheSettings.newBuilder().build()
                        )
                        .build();

        firestore.setFirestoreSettings(settings);

        authRepository = new AuthRepository(
                FirebaseAuth.getInstance(firebaseApp),
                firestore
        );
    }

    public AuthRepository getAuthRepository() {
        return authRepository;
    }
}