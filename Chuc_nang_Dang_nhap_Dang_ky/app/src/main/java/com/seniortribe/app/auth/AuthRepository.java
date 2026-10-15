package com.seniortribe.app.auth;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AuthRepository {

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    public AuthRepository(
            FirebaseAuth auth,
            FirebaseFirestore firestore
    ) {
        this.auth = auth;
        this.firestore = firestore;
    }

    public FirebaseUser currentUser() {
        return auth.getCurrentUser();
    }

    public boolean isCurrentUser(String uid) {
        FirebaseUser user = currentUser();

        return user != null
                && user.getUid().equals(uid);
    }

    public Task<FirebaseUser> signIn(
            String email,
            String password
    ) {
        return auth.signInWithEmailAndPassword(
                InputValidator.normalizeEmail(email),
                password
        ).onSuccessTask(result -> {
            FirebaseUser user = result.getUser();

            if (user == null) {
                return Tasks.forException(
                        new IllegalStateException("SESSION")
                );
            }

            return Tasks.forResult(user);
        });
    }

    public Task<FirebaseUser> signUp(
            String name,
            String email,
            String password
    ) {
        return auth.createUserWithEmailAndPassword(
                InputValidator.normalizeEmail(email),
                password
        ).onSuccessTask(result -> {
            FirebaseUser user = result.getUser();

            if (user == null) {
                return Tasks.forException(
                        new IllegalStateException("SESSION")
                );
            }

            UserProfileChangeRequest request =
                    new UserProfileChangeRequest.Builder()
                            .setDisplayName(name.trim())
                            .build();

            // Tai khoan da duoc tao.
            // Loi cap nhat ten Auth khong duoc khien nguoi dung
            // phai dang ky lai. Ho so chinh nam trong Firestore.
            return user.updateProfile(request)
                    .continueWith(task -> user);
        });
    }

    public Task<Void> sendPasswordReset(String email) {
        return auth.sendPasswordResetEmail(
                InputValidator.normalizeEmail(email)
        );
    }

    public Task<UserProfile> ensureProfile(String preferredName) {
        FirebaseUser user = currentUser();

        if (user == null) {
            return Tasks.forException(
                    new IllegalStateException("SESSION")
            );
        }

        String uid = user.getUid();

        String name;
        if (InputValidator.validName(preferredName)) {
            name = preferredName.trim();
        } else if (InputValidator.validName(user.getDisplayName())) {
            name = user.getDisplayName().trim();
        } else {
            name = "Thành viên SeniorTribe";
        }

        String email = user.getEmail();

        if (email == null) {
            return Tasks.forException(
                    new IllegalStateException("PROFILE")
            );
        }

        DocumentReference document = firestore
                .collection("users")
                .document(uid);

        return firestore.runTransaction(transaction -> {
            if (!isCurrentUser(uid)) {
                throw new IllegalStateException("SESSION");
            }

            DocumentSnapshot existing = transaction.get(document);

            if (existing.exists()) {
                String savedName =
                        existing.getString("displayName");
                String savedEmail =
                        existing.getString("email");
                String savedArea =
                        existing.getString("area");

                if (!uid.equals(existing.getString("uid"))
                        || !InputValidator.validName(savedName)
                        || savedEmail == null
                        || savedArea == null) {
                    throw new IllegalStateException("PROFILE");
                }

                return new UserProfile(
                        uid,
                        savedName,
                        savedEmail,
                        savedArea
                );
            }

            Map<String, Object> data = new HashMap<>();
            data.put("uid", uid);
            data.put("displayName", name);
            data.put("email", email);
            data.put("area", "");
            data.put("createdAt", FieldValue.serverTimestamp());
            data.put("updatedAt", FieldValue.serverTimestamp());

            transaction.set(document, data);

            return new UserProfile(uid, name, email, "");
        });
    }

    public Task<Boolean> hasAssignedMembership(String uid) {
        return firestore
                .collection("memberships")
                .document(uid)
                .collection("organizations")
                .get(Source.SERVER)
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        Exception error = task.getException();

                        if (error != null) {
                            throw error;
                        }

                        throw new IllegalStateException("MEMBERSHIP");
                    }

                    if (!isCurrentUser(uid)) {
                        throw new IllegalStateException("SESSION");
                    }

                    boolean assigned = false;

                    for (DocumentSnapshot membership
                            : task.getResult().getDocuments()) {

                        if (!Boolean.TRUE.equals(
                                membership.getBoolean("active"))) {
                            continue;
                        }

                        Object value = membership.get("roles");

                        if (!(value instanceof List)
                                || ((List<?>) value).isEmpty()) {
                            throw new IllegalStateException(
                                    "MEMBERSHIP"
                            );
                        }

                        for (Object role : (List<?>) value) {
                            if (!OrganizationRole.isKnown(role)) {
                                throw new IllegalStateException(
                                        "MEMBERSHIP"
                                );
                            }
                        }

                        assigned = true;
                    }

                    return assigned;
                });
    }

    public void signOut() {
        auth.signOut();
    }
}