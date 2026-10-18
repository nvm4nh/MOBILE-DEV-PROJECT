package com.seniortribe.app.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/** Read-only listener on the current account's organization memberships. */
public final class FirestoreMembershipWatch implements LiveAccessMonitor.Source {
    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;
    private final Executor executor;

    public FirestoreMembershipWatch(
            FirebaseFirestore firestore,
            FirebaseAuth auth,
            Executor executor
    ) {
        this.firestore = firestore;
        this.auth = auth;
        this.executor = executor;
    }

    @Override
    public String currentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? "" : user.getUid();
    }

    @Override
    public LiveAccessMonitor.Handle listen(
            String uid,
            LiveAccessMonitor.Observer observer
    ) {
        ListenerRegistration registration = firestore.collection("memberships")
                .document(uid)
                .collection("organizations")
                .addSnapshotListener(executor, MetadataChanges.INCLUDE, (snapshot, error) -> {
                    if (error != null) {
                        observer.error(error);
                        return;
                    }

                    if (snapshot == null) {
                        observer.error(new IllegalStateException("MEMBERSHIP"));
                        return;
                    }

                    boolean cache = snapshot.getMetadata().isFromCache();
                    boolean pending = snapshot.getMetadata().hasPendingWrites();

                    if (cache || pending) {
                        observer.snapshot(cache, pending, Collections.emptyList());
                        return;
                    }

                    List<LiveAccessMonitor.Grant> grants = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        grants.add(new LiveAccessMonitor.Grant(
                                document.getId(),
                                document.get("active"),
                                document.get("roles")
                        ));
                    }

                    observer.snapshot(false, false, grants);
                });

        return registration::remove;
    }
}